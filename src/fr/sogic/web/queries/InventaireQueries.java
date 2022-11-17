package fr.sogic.web.queries;

import fr.sogic.erp.core.queries.DBManager;
import fr.sogic.web.entities.Inventaire;
import fr.sogic.web.entities.LigneInventaire;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InventaireQueries {

    protected static final Logger logger = LogManager.getLogger();

    public static Inventaire insertInventaire(Inventaire inventaire) throws SQLException {

        String sql = "insert into televente.mouvement (bordereau,societe,type_mouv,fille, clifac, date_com, date_liv, date_es, etat, preparateur, ref_client, synchronisation, valide) values (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        boolean manageTransaction = !DBManager.isTransactional();
        boolean commit = false;
        Connection connection = DBManager.startTransactionalConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            connection.setAutoCommit(false);
            if (inventaire.getNumBordereau() == 0) {
                inventaire.setNumBordereau(generate(inventaire.getCodeSociete()));
                int index = 0;
                statement.setInt(++index, inventaire.getNumBordereau());
                statement.setInt(++index, inventaire.getCodeSociete());
                statement.setString(++index, "I");
                statement.setString(++index, "$000");
                statement.setString(++index, "0000");
                statement.setDate(++index, java.sql.Date.valueOf(inventaire.getDateBordereau()));
                statement.setDate(++index, java.sql.Date.valueOf(inventaire.getDateBordereau()));
                statement.setDate(++index, java.sql.Date.valueOf(inventaire.getDateBordereau()));
                statement.setInt(++index, 4);
                statement.setString(++index, inventaire.getCodePreparateur());
                statement.setString(++index, inventaire.getCommentaire());
                statement.setString(++index, "O");
                statement.setString(++index, "O");
                if (statement.executeUpdate() == 0)
                    throw new IllegalArgumentException("Aucune insertion (inventaire) effectuée");
                else
                    logger.info("[insertInventaire] Inventaire inséré : {}", inventaire.dump());
                commit = true;
            }
            for (LigneInventaire ligne : inventaire.getLignesInventaire())
                insertLigneInventaire(ligne);
        } catch (SQLException e) {
            logger.trace("[insertInventaire] Executed SQL Query : " + sql);
            logger.error("[insertInventaire] ", e);
            throw e;
        } finally {
            if(manageTransaction) {
                if(commit)
                    connection.commit();
                else
                    connection.rollback();
                DBManager.close(connection);
            }
        }
        return inventaire;
    }

    private static LigneInventaire insertLigneInventaire(LigneInventaire inventaireLigne) throws SQLException {

        String sql = "insert into televente.mouvement_ligne (bordereau,societe,type_mouv,ligne, article, colis_com, nombre_com, qte_com, colis_liv, nombre_liv, qte_liv, tarif, remise, puht, tarif_gen, manquant,  date_dlc, date_liv, date_es, pachat, pnet,pbase) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        Connection connection = DBManager.getTransactionalConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            int index = 0;
            statement.setInt(++index, inventaireLigne.getInventaire().getNumBordereau());
            statement.setInt(++index, inventaireLigne.getInventaire().getCodeSociete());
            statement.setString(++index, "I");
            statement.setInt(++index, inventaireLigne.getNumLigne());
            statement.setString(++index, inventaireLigne.getArticle().getCodeProduit());
            statement.setDouble(++index,0.0);
            statement.setDouble(++index,0.0);
            statement.setDouble(++index,0.0);
            statement.setDouble(++index,0.0);
            statement.setDouble(++index,0.0);
            statement.setDouble(++index, inventaireLigne.getQteUVLivrees());
            statement.setBigDecimal(++index, inventaireLigne.getPuHt());
            statement.setDouble(++index, 0.0);
            statement.setBigDecimal(++index, inventaireLigne.getPuHt());
            statement.setBigDecimal(++index, inventaireLigne.getPuHt());
            statement.setString(++index, "N");
            statement.setDate(++index, inventaireLigne.getDlc() != null ? java.sql.Date.valueOf(inventaireLigne.getDlc()): null);
            statement.setDate(++index, java.sql.Date.valueOf(inventaireLigne.getInventaire().getDateBordereau()));
            statement.setDate(++index, java.sql.Date.valueOf(inventaireLigne.getInventaire().getDateBordereau()));
            statement.setBigDecimal(++index, inventaireLigne.getPuHt());
            statement.setBigDecimal(++index, inventaireLigne.getPuHt());
            statement.setBigDecimal(++index, inventaireLigne.getPuHt());

            if(statement.executeUpdate() == 0)
                throw new IllegalArgumentException("Aucune insertion (ligne) effectuée");
            else
                logger.info("[insertLigneInventaire] Ligne inventaire insérée : {}", inventaireLigne);
        } catch (SQLException e) {
            logger.trace("[insertLigneInventaire] Executed SQL Query : " + sql);
            logger.error("[insertLigneInventaire] ", e);
            throw e;
        }
        return inventaireLigne;

    }


    public static Integer generate(int societe) {
        int numBordereau;
        do {
            numBordereau = selectNextNumero(societe, "INVENTAIRE");
        } while(isNumBordereauUsed(societe, numBordereau));
        logger.debug("[generateBordereau] Numéro de bordereau généré pour le mouvement {}: {}", numBordereau, societe);
        return numBordereau;
    }

    public static int selectNextNumero(int codeSociete, String counterName) {

        int cpt = 0;
        String sql = "select " + counterName + " from televente.compteur_commande where societe=? for update of " + counterName;
        Connection dbConnection = DBManager.isTransactional() ? DBManager.getTransactionalConnection() : DBManager.getConnection();
        try (PreparedStatement pstm = dbConnection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)) {
            pstm.setInt(1, codeSociete);
            ResultSet res = pstm.executeQuery();
            if (!res.next()) {
                logger.error("[selectNextNumero] SQL statement to get an counter number returned no result. Cannot find a counter for parameters {} - {}, return -1 by default.", codeSociete, counterName);
                return -1;
            }
            cpt = res.getInt(1) + 1;
            res.updateInt(1, cpt);
            res.updateRow();
        } catch (SQLException e) {
            logger.trace("[selectNextNumero] Query : " + sql);
            logger.error("[selectNextNumero] ", e);
            throw new RuntimeException(e);
        } finally {
            if(!DBManager.isTransactional())
                DBManager.close(dbConnection);
        }
        return cpt;
    }

    //Verifier que le bordereau n'est pas utilisé sinon insertion impossible à cause des clés primaires
    public static boolean isNumBordereauUsed(int societe, int numBordereau) {
        String sql = "select 1 from televente.mouvement m where m.type_mouv = 'I' and m.societe = ? and m.bordereau = ?";
        Connection dbConnection = DBManager.isTransactional() ? DBManager.getTransactionalConnection() : DBManager.getConnection();
        boolean used = false;
        try (PreparedStatement statement = dbConnection.prepareStatement(sql)){
            statement.setInt(1, societe);
            statement.setInt(2, numBordereau);
            ResultSet result = statement.executeQuery();
            if (result.next()) {
                used = true;
            }
        } catch (SQLException e) {
            logger.error("[isNumBordereauUsed] " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if(!DBManager.isTransactional())
                DBManager.close(dbConnection);
        }
        return used;

    }
}

