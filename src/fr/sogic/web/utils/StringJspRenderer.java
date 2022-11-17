package fr.sogic.web.utils;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class StringJspRenderer {

    public static class StringHttpServletResponse extends HttpServletResponseWrapper {

        private StringWriter sw = new StringWriter();

        public StringHttpServletResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    sw.write(b);
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            PrintWriter pw = new PrintWriter(sw);
            return pw;
        }

        public String toString() {
            return sw.getBuffer().toString();
        }
    }

    public static String renderJspToString(String jspPath, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        StringHttpServletResponse stringResponse = new StringHttpServletResponse(response);
        request.getServletContext().getRequestDispatcher(jspPath).forward(request, stringResponse);
        return stringResponse.toString();
    }
}
