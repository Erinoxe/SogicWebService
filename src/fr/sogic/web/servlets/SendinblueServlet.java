package fr.sogic.web.servlets;

import fr.sogic.erp.core.SERPUtils;
import fr.sogic.erp.core.services.EmailService;
import fr.sogic.utils.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = {"/sendinblue"})
public class SendinblueServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String jsonString = request.getReader().lines().collect(Collectors.joining());
        logger.debug("[doGet] Received data : {}", jsonString);
        if(StringUtils.isNotEmpty(jsonString)) {
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                String event = jsonObject.getString("event");
                String error = null;
                if ("soft_bounce".equals(event)) {
                    error = "Le message n'a pas pu être remis au destinataire car une erreur inconnue est survenue (le message a par exemple pu être bloqué par un pare-feu).";
                } else if ("hard_bounce".equals(event)) {
                    error = "Le message ne peut pas être délivré au destinataire spécifié (il s'agit peut-être d'une adresse mail inexistante ?).";
                } else if ("blocked".equals(event)) {
                    error = "Le message ne peut pas être délivré au destinataire car il a été bloqué lors de l'envoi (probablement à cause d'une adresse invalide).";
                } else if ("deferred".equals(event)) {
                    error = "Le message a été différé, il sera remis au destinataire à un horaire ultérieur inconnu.";
                }
                if (error != null) {
                    String recipient = jsonObject.getString("email");
                    String subject = "[Sans objet]";
                    try {
                        subject = jsonObject.getString("subject");
                    } catch(JSONException ex) {
                        logger.debug("[doGet] Champ \"subject\" absent du JSON");
                    }
                    String messageId = jsonObject.getString("message-id");
                    String reason = error;
                    try {
                        reason = jsonObject.getString("reason");
                    } catch(JSONException ex) {
                        logger.debug("[doGet] Champ \"reason\" absent du JSON");
                    }
                    LocalDateTime dateTime = DateUtils.parseTime(jsonObject.getString("date"), "yyyy-MM-dd HH:mm:ss");
                    String sender = null;
                    // Les Messages-ID contenant le caractère "|" sont très probablement des message-ID personnalisés par SogicCore.
                    // Dans ce cas, ils contiennent l'adresse de l'expéditeur.
                    if (messageId.contains("|") && messageId.indexOf("|") != messageId.length() - 1)
                        sender = messageId.split("\\|")[1].replace(">", "").replace("<", "");
                    logger.debug("[doGet] Extracted data : recipient={}, subject={}, messageId={}, dateTime={}, sender={}", recipient, subject, messageId, DateUtils.format(dateTime, "dd/MM/yyyy HH:mm"), sender);
                    // Envoi d'un mail pour avertir de l'erreur :
                    // - à l'exploitation si l'expéditeur du mail est inconnu
                    // - à l'expéditeur s'il est connu
                    String msgBody = "Attention,<br/><br/>l'envoi du mail \"<i>" + subject + "</i>\" du " + DateUtils.format(dateTime, "dd/MM/yyyy HH:mm") + " à destination de <b>" + recipient + "</b> a échoué.<br/><br/><b>Raison :</b> " + reason + "<br/><b>Evénement :</b> " + event + "<br/><b>Message ID :</b> " + messageId + "<br/><br/>Ceci est un message automatique généré par le système, merci de ne pas y répondre.";
                    SERPUtils.setEnvironment(SERPUtils.Environment.PROD);
                    EmailService.sendEmail("L'envoi d'un mail a échoué", new String[]{StringUtils.isEmpty(sender) || sender.contains("noreply") ? "michael.morelli@sogic.fr" : sender}, "noreply.sws@sogic.fr", "Sogic WebService", null, msgBody, false);
                }
            } catch(JSONException ex) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().println("Mauvais format de données. Le service attend des données au format JSON défini par le protocole de Sendinblue. Données reçues : " + jsonString);
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            response.getWriter().println("Aucune donnée reçue. Le service fonctionne correctement mais attend des données au format JSON défini par le protocole de Sendinblue.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.doGet(request, response);
    }
}
