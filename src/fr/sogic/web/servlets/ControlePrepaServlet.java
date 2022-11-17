package fr.sogic.web.servlets;


import fr.sogic.erp.core.entities.*;
import fr.sogic.erp.core.queries.*;
import fr.sogic.web.utils.XmlUtils;
import fr.sogic.web.utils.ServletUtils;
import javafx.collections.FXCollections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import java.util.List;

@WebServlet(urlPatterns = {"/prepa"})
public class ControlePrepaServlet extends HttpServlet {

//    @Resource(name = "jdbc/televenteDB")
    @Resource(name = "jdbc/televenteDB2")
    private DataSource dataSource;

    private static final Logger logger = LogManager.getLogger();

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

    public void getControleurs(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, TransformerException, IOException {
        Document document = XmlUtils.createDocument();
        List<Utilisateur> controleurs = UtilisateurQueries.selectUtilisateurs(null, null, true);
        if (!controleurs.isEmpty()) {
            Element root = document.createElement("Controleurs");
            document.appendChild(root);
            controleurs.forEach(controleur -> {
                Element domResponsable = document.createElement("Controleur");
                root.appendChild(domResponsable);

                Element code = document.createElement("Code");
                domResponsable.appendChild(code);
                code.appendChild(document.createTextNode(controleur.getLogin()));

                Element nom = document.createElement("Designation");
                domResponsable.appendChild(nom);
                nom.appendChild(document.createTextNode(controleur.getNomComplet()));
            });
        }
        XmlUtils.renderXML(document, response);
    }

    public void getPreparation(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, TransformerException, IOException {
        try {
            Integer preparation = Integer.parseInt(ServletUtils.getMandatoryParameter(request, "preparation"));
            List<Vente> preparationVentes = VenteQueries.selectVentesForPreparation(preparation);
            if(!preparationVentes.isEmpty()) {
                Document document = XmlUtils.createDocument();
                Element root = document.createElement("Ventes");
                document.appendChild(root);
                preparationVentes.forEach(vente -> {
                    vente.setLignes(FXCollections.observableArrayList(VenteQueries.selectLignesVente(vente)));
                    Element domVente = document.createElement("Vente");
                    domVente.setAttribute("bordereau", String.valueOf(vente.getNumBordereau()));
                    domVente.setAttribute("depot", String.valueOf(vente.getCodeSociete()));
                    domVente.setAttribute("typeMouv", vente.getTypeMouvement().getLetter());
                    root.appendChild(domVente);

                    Element domLignes = document.createElement("Lignes");
                    domVente.appendChild(domLignes);
                    vente.getLignesVente().forEach(ligneVente -> {
                        Element domLigne = document.createElement("Ligne");
                        domLignes.appendChild(domLigne);
                        Element domNumLigne = document.createElement("Numero");
                        domNumLigne.appendChild(document.createTextNode(String.valueOf(ligneVente.getNumLigne())));
                        domLigne.appendChild(domNumLigne);
                        Element domArticle = document.createElement("Article");
                        domArticle.appendChild(document.createTextNode(String.valueOf(ligneVente.getArticle().getCodeProduit())));
                        domLigne.appendChild(domArticle);
                        Element domQte = document.createElement("Quantite");
                        domQte.appendChild(document.createTextNode(String.valueOf(ligneVente.getQteUVLivrees())));
                        domLigne.appendChild(domQte);
                        Element domNc = document.createElement("NCPrepa");
                        domNc.appendChild(document.createTextNode(ligneVente.getCodeNCPrepa() != null ? String.valueOf(ligneVente.getCodeNCPrepa().getCode()) : ""));
                        domLigne.appendChild(domNc);
                    });
                });
                XmlUtils.renderXML(document, response);
            }
        } catch(NumberFormatException ex) {
            String output = "N° de prépa invalide : " + request.getParameter("preparation");
            logger.warn("[getPreparation] " + response);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(output);
        }
    }

    public void getCodesNCPrepa(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, TransformerException, IOException {
        List<CodeNC> codesNCPrepa = CodeNC.getValuesForPrepa();
        if(!codesNCPrepa.isEmpty()) {
            Document document = XmlUtils.createDocument();
            Element root = document.createElement("CodesNC");
            document.appendChild(root);
            codesNCPrepa.forEach(codeNC -> {
                Element domCodeNC = document.createElement("CodeNC");
                root.appendChild(domCodeNC);

                Element domCode = document.createElement("Code");
                domCode.appendChild(document.createTextNode(String.valueOf(codeNC.getCode())));
                domCodeNC.appendChild(domCode);
                Element domDesignation = document.createElement("Designation");
                domDesignation.appendChild(document.createTextNode(codeNC.getLibelle()));
                domCodeNC.appendChild(domDesignation);
            });
            XmlUtils.renderXML(document, response);
        }
    }

    public void updatePreparation(HttpServletRequest request, HttpServletResponse response) throws ParserConfigurationException, IOException, SAXException {
//        logger.trace("[updatePreparation] " + ServletUtils.dumpRequest(request));

        DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
        DocumentBuilder constructeur = fabrique.newDocumentBuilder();
        Document document = constructeur.parse(request.getInputStream());

        String loginControleur = document.getElementsByTagName("Controleur").item(0).getTextContent();
        Utilisateur controleur = UtilisateurQueries.selectUtilisateurByLogin(loginControleur);
        if(loginControleur != null && controleur == null) {
            logger.error("[updatePreparation] Impossible de trouver un controleur (utilisateur) correspondant au login " + loginControleur);
            response.setStatus(400);
            return;
        }

        NodeList venteNodes = document.getElementsByTagName("Vente");
        for(int i=0; i<venteNodes.getLength(); i++) {
            Element commandeElement = (Element) venteNodes.item(i);
            Vente commande = new Vente();
            commande.setNumBordereau(Integer.parseInt(commandeElement.getAttribute("bordereau")));
            commande.setCodeSociete(Integer.parseInt(commandeElement.getAttribute("depot")));
            VenteQueries.selectVenteInto(commande);
            commande.setLignes(FXCollections.observableArrayList(VenteQueries.selectLignesVente(commande)));
            if(controleur.getId() != commande.getIdControleur()) {
                commande.setIdControleur(controleur.getId());
                MouvementQueries.updateMouvement(commande);
            }
            NodeList ligneNodes = commandeElement.getElementsByTagName("Ligne");
            for(int j=0; j<ligneNodes.getLength(); j++) {
                Element ligne = (Element) ligneNodes.item(j);
                int numLigne = Integer.parseInt(ligne.getElementsByTagName("Numero").item(0).getTextContent());
                LigneVente ligneVente = commande.getLignesVente().stream().filter(line -> line.getNumLigne() == numLigne).findFirst().orElse(null);
                if(ligneVente != null) {
                    if (ligne.getElementsByTagName("NCPrepa").getLength() != 0) {
                        String code = ligne.getElementsByTagName("NCPrepa").item(0).getTextContent();
                        CodeNC codeNCPrepa = CodeNC.getFromCode(code);
                        if(codeNCPrepa != null && !codeNCPrepa.equals(ligneVente.getCodeNCPrepa())) {
                            ligneVente.setCodeNCPrepa(codeNCPrepa);
                            MouvementQueries.updateLigneMouvement(ligneVente);
                        } else if (codeNCPrepa == null) {
                            logger.error("[updatePreparation] Impossible de trouver un code NC correspondant à " + code);
                            response.setStatus(400);
                            return;
                        }
                    } else if (ligneVente.getCodeNCPrepa() != null) {
                        ligneVente.setCodeNCPrepa(null);
                        MouvementQueries.updateLigneMouvement(ligneVente);
                    }
                } else {
                    logger.error("[updatePreparation] Impossible de trouver une ligne portant le numéro " + numLigne +" dans la commande " + commande);
                    response.setStatus(400);
                    return;
                }
            }
        }
        response.setStatus(200);
    }

}
