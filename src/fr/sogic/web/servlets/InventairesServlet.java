package fr.sogic.web.servlets;

import fr.sogic.erp.core.entities.Article;
import fr.sogic.erp.core.entities.Societe;
import fr.sogic.erp.core.queries.ArticleQueries;
import fr.sogic.erp.core.queries.DBManager;
import fr.sogic.erp.core.queries.SocieteQueries;
import fr.sogic.erp.core.services.ConfigService;
import fr.sogic.utils.FileUtils;
import fr.sogic.web.queries.InventaireQueries;
import fr.sogic.web.utils.XmlUtils;
import fr.sogic.utils.DateUtils;
import fr.sogic.web.entities.Inventaire;
import fr.sogic.web.entities.LigneInventaire;
import fr.sogic.web.utils.ServletUtils;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@WebServlet(urlPatterns = {"/inventaires"})
public class InventairesServlet extends HttpServlet {

    @Resource(name = "jdbc/televenteDB")
    private DataSource dataSource;

    protected static final Logger logger = LogManager.getLogger();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        DBManager.setDataSource(dataSource);
        response.setContentType("text/xml");
        ServletUtils.invokeMethodByRequest(request, response, this);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.doGet(request, response);
    }


    public void addLineInventaire(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, IOException, SAXException, SQLException, TransformerException {
        int societe = Integer.parseInt(request.getParameter("societe"));

        DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
        DocumentBuilder constructeur = fabrique.newDocumentBuilder();

        Document document = constructeur.parse(request.getInputStream());

        NodeList nodeList = document.getElementsByTagName("Bordereau");
        if(nodeList.getLength() == 0) {
            Inventaire inventaire = new Inventaire();
            //inventaire.setBordereau(Integer.parseInt(nodeList.item(0).getTextContent()));
            inventaire.setCodeSociete(societe);
            inventaire.setCommentaire(document.getElementsByTagName("Commentaire").item(0).getTextContent());
            inventaire.setCodePreparateur(document.getElementsByTagName("Responsable").item(0).getTextContent());
            inventaire.setDateBordereau(DateUtils.parse(document.getElementsByTagName("Date").item(0).getTextContent(), "yyyy-MM-dd"));

            // Add lines
            NodeList nodeLignes = document.getElementsByTagName("Ligne");
            for(int i=0; i<nodeLignes.getLength(); i++) {
                Element ligne = (Element) nodeLignes.item(i);
                Article article = ArticleQueries.selectArticle(ligne.getElementsByTagName("Article").item(0).getTextContent(), societe);
                LigneInventaire inventaireLigne = new LigneInventaire(article, Double.parseDouble(ligne.getElementsByTagName("Qte").item(0).getTextContent().replace(",",".")));
                inventaireLigne.setInventaire(inventaire);
                inventaireLigne.setNumLigne(inventaire.getNumLigneMax()+1);
                String dlcString = ligne.getElementsByTagName("Dlc").item(0).getTextContent();
                if(!StringUtils.isEmpty(dlcString) && !"null".equalsIgnoreCase(dlcString))
                    inventaireLigne.setDlc(DateUtils.parse(dlcString, "yyyy-MM-dd"));
                inventaireLigne.setPuHt(ArticleQueries.selectTarifNetNet(article, inventaire.getDateBordereau()));
                inventaire.addLigne(inventaireLigne);
            }
            inventaire.computeTotalHtLiv();
            inventaire.computePoidsLivr();

            InventaireQueries.insertInventaire(inventaire);

            Node bordereau = document.createElement("Bordereau");
            bordereau.appendChild(document.createTextNode(inventaire.getNumBordereau()+""));

            document.getElementsByTagName("Inventaire").item(0).appendChild(bordereau);

        }

        document.getElementsByTagName("Inventaire").item(0).removeChild(document.getElementsByTagName("Lignes").item(0));

        response.setStatus(200);
        XmlUtils.renderXML(document, response);

    }

    public void roll(HttpServletRequest request, HttpServletResponse response) throws IOException, ParserConfigurationException, TransformerException {
//        int societe = Integer.parseInt(request.getParameter("societe"));

        List<String> commandes = Arrays.stream(request.getParameterValues("commandes")).collect(Collectors.toList());
        String imprimante = request.getParameter("imprimante");
        // Si l'imprimante n'a pas été spécifiée en paramètre (normalement ce n'est plus censé être le cas, mais ce code est conservée
        // pour la compatibilité rétroactive)
        if(imprimante == null) {
            // On va déterminer le "dépot de référence" des commandes présentes sur la fiche de roll, pour savoir quelle imprimante utiliser pour l'impression
            // Pour cela on inspecte les lettres préfixes des bordereaux et on en déduit la liste des dépots
            List<Integer> societes = commandes.stream().map(bordereau ->
                            Societe.getValues().stream().filter(societe -> societe.getLettre().equals(bordereau.substring(0, 1))).findFirst().get().getId())
                    .distinct().collect(Collectors.toList());
            // En général, prendre n'importe quelle société de la liste devrait donner la même imprimante, mais il y a tout de même un cas où cela
            // s'avère faux : lorsqu'il y a du GEL GMS dans la commande, qui sont des produits SEDAGEL préparés à KALLIGEL. Pour sécuriser ces cas,
            // on utilise donc d'office l'imprimante de POYET lorsque des commandes POYET ou KALLIGEL se trouvent dans le roll.
            imprimante = ConfigService.getPropertyForSociete("PRINTER_DEFAULT_DEPOT", societes.get(0));
            if (societes.contains(Societe.POYET) || societes.contains(Societe.KALLIGEL))
                imprimante = ConfigService.getPropertyForSociete("PRINTER_DEFAULT_DEPOT", Societe.POYET);
        }
        Document document = XmlUtils.createDocument();
        // root element
        Element root = document.createElement("Client");
        document.appendChild(root);
        Element result = document.createElement("Result");
        root.appendChild(result);

        try {
            if (printRoll(imprimante, commandes/*, societe*/)) {
                result.appendChild(document.createTextNode("Ok"));
            }
        } catch (JRException e) {
            result.appendChild(document.createTextNode("Erreur lors de l'impression"));
        }

        XmlUtils.renderXML(document, response);

    }

    private boolean printRoll(String imprimante, List<String> commandes) throws JRException {
        try(Connection oracle = DBManager.getConnection()) {
            Map<String, Object> params = new HashMap<>();

            params.put("NUMEROS", commandes.stream().collect(Collectors.joining("','","'","'")));

            InputStream reportResource = this.getClass().getClassLoader().getResourceAsStream("reports/Roll2.jasper");
            JasperPrint jasperPrint = JasperFillManager.fillReport(reportResource, params, oracle);
            return FileUtils.printReportToPrinter(jasperPrint, imprimante != null ? imprimante : "Informatique_OptraN", 1);

        } catch (SQLException ex) {
            logger.error("[printRoll] Unable to obtain Connection ", ex);
            return false;
        }
    }

}
