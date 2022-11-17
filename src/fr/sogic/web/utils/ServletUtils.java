package fr.sogic.web.utils;

import fr.sogic.erp.core.exceptions.RecoverableException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.servlet.http.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class ServletUtils {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Automatically invoke the method specified by the "action" request parameter
     * WARNING : This snippet makes all methods available for invocation by URL
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public static void invokeMethodByRequest(HttpServletRequest request, HttpServletResponse response, HttpServlet servlet) throws IOException {
        String action = request.getParameter("action");
        if (action == null || action.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Aucune action spécifiée");
        } else {
            try {
                // KEY POINT : Invoke the requested method
                logger.debug("[invokeMethodByRequest] Try to invoke method " + action + " on servlet " + servlet.getClass().getSimpleName());
                Method method = servlet.getClass().getMethod(action, HttpServletRequest.class, HttpServletResponse.class);
                method.invoke(servlet, request, response);
            } catch (SecurityException | NoSuchMethodException | IllegalArgumentException | IllegalAccessException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("L'action spécifiée est invalide (" + e.getMessage() + ")");
            } catch (InvocationTargetException e) {
                logger.error("[invokeMethodByRequest] InvocationTargetException : ", e.getTargetException());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Une erreur est survenue durant l'exécution de l'action spécifiée : " + e.getTargetException().getClass().getName() + " - " + e.getTargetException().getMessage() + ". " + Arrays.stream(e.getCause().getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining("\n", "\n", "\n")));
                // Rethrow this exception so that it can bubble up to the ErrorManager :
//                if(e.getTargetException() instanceof RecoverableException)
//                    throw (RecoverableException)e.getTargetException();
//                else if(e.getTargetException() instanceof RuntimeException)
//                    throw (RuntimeException)e.getTargetException();
//                else
//                    throw new RuntimeException(e.getTargetException());
            }
        }
    }

    /**
     * Initialize only fixed or unique attributes such as...
     * @param request The HTTP request
     */
    public static void initializeSession(HttpServletRequest request) {
        // Create a new HTTP Session. This is the ONLY place we create a session, so we can use this initialization to set global settings (such as timeout)
        HttpSession session = request.getSession(true);
//        session.setMaxInactiveInterval(Config.getSessionTimeout() * 60); // In seconds
//        session.setAttribute("user", user);
    }

    public static String getPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    public static String getParametersLog(HttpServletRequest request) {
        if(request != null && request.getParameterMap() != null && !request.getParameterMap().isEmpty())
            return request.getParameterMap().entrySet().stream().map(entry -> entry.getKey() +"="+ Arrays.stream(entry.getValue()).collect(Collectors.joining("[",",","]"))).collect(Collectors.joining(", "));
        return null;
    }

    public static void addResponseField(HttpServletRequest request, String fieldName, Object fieldValue) {
        JSONObject jsonResponse = (JSONObject) request.getAttribute("jsonResponse");
        jsonResponse.put(fieldName, fieldValue);
    }

    public static String getMandatoryParameter(HttpServletRequest request, String paramName) {
        String paramValue = request.getParameter(paramName);
        if(paramValue == null || paramValue.isEmpty())
            throw new RecoverableException("Paramètre obligatoire manquant dans la requête : " + paramName);
        return paramValue;
    }

    public static boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    /**
     * Inspired by : https://gist.github.com/pdmyrs/8ff4b14e985e4885b92e7c804b69ef89
     */
    public static String dumpRequest(HttpServletRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("receive " + request.getMethod() +" notification for "+ request.getRequestURI());

        stringBuilder.append("\n\nHeaders :");
        Enumeration headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String headerName = (String)headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            stringBuilder.append("\n" + headerName + " = " + headerValue);
        }

        stringBuilder.append("\n\nCookies :");
        Cookie cookies [] = request.getCookies();
        if(ArrayUtils.isNotEmpty(cookies)) {
            for (Cookie cookie : cookies) {
                String cookieName = cookie.getName();
                String cookieValue = cookie.getValue();
                String cookieDomain = cookie.getDomain();
                String cookiePath = cookie.getPath();
                stringBuilder.append("\n" + cookieName + " = " + cookieValue + "; domain=" + cookieDomain + "; path=" + cookiePath);
            }
        }

        stringBuilder.append("\n\nParameters :");
        Enumeration params = request.getParameterNames();
        while(params.hasMoreElements()){
            String paramName = (String)params.nextElement();
            String paramValue = request.getParameter(paramName);
            stringBuilder.append("\n" + paramName + " = " + paramValue);
        }

//        stringBuilder.append("\n\nRaw data :\n");
//        if ("POST".equalsIgnoreCase(request.getMethod())) {
//            try {
//                Scanner s = new Scanner(request.getInputStream(), "UTF-8").useDelimiter("\\A");
//                stringBuilder.append(s.hasNext() ? s.next() : "");
//            } catch (IOException e) {
//                logger.info("[dumpRequest] Fail to log raw data " , e);
//            }
//        }

        return stringBuilder.toString();
    }
}







