package fr.sogic.web.servlets;


import fr.sogic.erp.core.entities.*;
import fr.sogic.erp.core.queries.*;
import fr.sogic.web.utils.XmlUtils;
import fr.sogic.utils.CollectionUtils;
import fr.sogic.web.utils.ServletUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@WebServlet(urlPatterns = {"/*"})
public class CommonServlet extends HttpServlet {

//    @Resource(name = "jdbc/televenteDB")
    @Resource(name = "jdbc/televenteDB2")
    private DataSource dataSource;

    static final Logger logger = LogManager.getLogger();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        DBManager.setDataSource(dataSource);
        response.setContentType("text/xml");
        ServletUtils.invokeMethodByRequest(request, response, this);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.doGet(request, response);
    }

    public void getPreparateurs(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, TransformerException, IOException {
        Document document = XmlUtils.createDocument();

        List<Preparateur> responsables = PreparateurQueries.selectPreparateurs();
        if (!responsables.isEmpty()) {
            Element root = document.createElement("Preparateurs");
            document.appendChild(root);

            responsables.forEach(responsable -> {
                Element domResponsable = document.createElement("Preparateur");
                root.appendChild(domResponsable);

                Element code = document.createElement("Code");
                domResponsable.appendChild(code);
                code.appendChild(document.createTextNode(responsable.getCode()));

                Element nom = document.createElement("Designation");
                domResponsable.appendChild(nom);
                nom.appendChild(document.createTextNode(responsable.getDesignation()));
            });
        }
        XmlUtils.renderXML(document, response);

    }

    public void getFournisseurByCode(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, TransformerException, IOException {
        String codeFournisseur = request.getParameter("fournisseur");
        int societe = Integer.parseInt(request.getParameter("societe"));

        if(codeFournisseur != null) {
            Document document = XmlUtils.createDocument();
            Fournisseur fournisseur = FournisseurQueries.selectFournisseurById(codeFournisseur, societe);

            if (fournisseur != null) {
                // root element
                Element root = document.createElement("Fournisseur");
                document.appendChild(root);

                Element code = document.createElement("Code");
                code.appendChild(document.createTextNode(fournisseur.getCode()));

                Element nom = document.createElement("Nom");
                nom.appendChild(document.createTextNode(fournisseur.getNom()));

                root.appendChild(code);
                root.appendChild(nom);
            }

            XmlUtils.renderXML(document, response);

        }
    }

    public void getArticlesByFournisseur(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, TransformerException, IOException {
        int societe = Integer.parseInt(request.getParameter("societe"));
        String codeFournisseur = request.getParameter("fournisseur");
        Fournisseur fournisseur = FournisseurQueries.selectFournisseurById(codeFournisseur, societe);
        Document document = XmlUtils.createDocument();
        List<Article> articles = ArticleQueries.selectArticlesByFournisseur(fournisseur, true, "'AD', 'AP', 'AH', 'AS', 'AG'", false);
        toXml(articles, document);
        XmlUtils.renderXML(document, response);
    }

    public void getArticlesByEmplacement(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, TransformerException, IOException {
        String societe = request.getParameter("societe");
        String emplacementDebut = request.getParameter("emplacement_debut").trim();
        String emplacementFin = request.getParameter("emplacement_fin").trim();
        Document document = XmlUtils.createDocument();
        List<Article> articles = ArticleQueries.selectArticles(CollectionUtils.toMap(
                "societeFilter",  societe,
                "emplacementStart", emplacementDebut,
                "emplacementEnd", emplacementFin,
                "ctr4ExcludeFilter", "'AD', 'AP', 'AH', 'AS', 'AG'"
        ));
        articles.sort(Comparator.comparing(Article::getEmplacement));
        toXml(articles, document);
        XmlUtils.renderXML(document, response);
    }

    protected void toXml(List<Article> articles, Document document) {
        if (articles != null) {
            Element root = document.createElement("Articles");
            document.appendChild(root);

            articles.forEach(article -> {
                Element domArticle = document.createElement("Article");
                root.appendChild(domArticle);

                Element code = document.createElement("Code");
                domArticle.appendChild(code);
                code.appendChild(document.createTextNode(article.getCodeProduit()));

                Element nom = document.createElement("Designation");
                domArticle.appendChild(nom);
                nom.appendChild(document.createTextNode(article.getDesignation()));

                Element pcb = document.createElement("PCB");
                domArticle.appendChild(pcb);
                pcb.appendChild(document.createTextNode(article.getConditionnementColis().getQteUnit()+""));

                Element qte = document.createElement("Qte");
                domArticle.appendChild(qte);
                Double stockPhysique = (Double)ArticleQueries.getStock(article.getCodeDepot(), article.getCodeProduit(), LocalDate.now(), LocalDate.now()).get(1);
                qte.appendChild(document.createTextNode(String.valueOf(stockPhysique)));

                Element emplacement = document.createElement("Emplacement");
                domArticle.appendChild(emplacement);
                emplacement.appendChild(document.createTextNode(StringUtils.defaultString(article.getEmplacement(), "")));
            });
        }
    }

    public void getFilleForCommande(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, TransformerException, IOException {
        int depot = Integer.parseInt(request.getParameter("societe"));
        try {
            int numBordereau = Integer.parseInt(request.getParameter("commande"));
            Document document = XmlUtils.createDocument();
            Vente vente = new Vente();
            vente.setCodeDepot(depot);
            vente.setNumBordereau(numBordereau);
            VenteQueries.selectVenteInto(vente);
            Fille client = vente.getFille();
            if (client != null) {
                // root element
                Element root = document.createElement("Client");
                document.appendChild(root);

                Element code = document.createElement("Code");
                code.appendChild(document.createTextNode(client.getId()));

                Element nom = document.createElement("Nom");
                nom.appendChild(document.createTextNode(client.getNomUsuel() == null ? client.getRaisonSociale() : client.getNomUsuel()));

                root.appendChild(code);
                root.appendChild(nom);
            }
            XmlUtils.renderXML(document, response);
        } catch(NumberFormatException ex) {
            String output = "N° de commande invalide : " + request.getParameter("commande");
            logger.warn("[getFilleForCommande] " + response);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(output);
        }

    }
}
