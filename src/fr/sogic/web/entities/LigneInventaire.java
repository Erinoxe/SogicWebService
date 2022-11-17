package fr.sogic.web.entities;

import fr.sogic.erp.core.entities.Article;
import fr.sogic.erp.core.entities.LigneMouvement;
import fr.sogic.erp.core.entities.Mouvement;

public class LigneInventaire extends LigneMouvement {

    public LigneInventaire(Article article, double quantiteUnitaire) {
        setArticle(article);
        setQteUVLivrees(quantiteUnitaire);
        setTypeMouvement(Mouvement.TypeMouvement.Inventaire);
    }

    public Inventaire getInventaire() {
        return (Inventaire)getMouvement();
    }

    public void setInventaire(Inventaire inventaire) {
        setMouvement(inventaire);
    }


    @Override
    public String toString() {
        return "InventaireLigne{" +
                "article=" + getArticle()+
                "inventaire=" + getMouvement() +
                ", dlc=" + getDlc() +
                ", numLigne=" + getNumLigne() +
                '}';
    }
}
