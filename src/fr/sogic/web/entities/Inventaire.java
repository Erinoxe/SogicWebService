package fr.sogic.web.entities;

import fr.sogic.erp.core.entities.Mouvement;
import fr.sogic.erp.core.entities.Preparateur;
import javafx.collections.ObservableList;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

public class Inventaire extends Mouvement {

	public Inventaire() {
		setTypeMouvement(TypeMouvement.Inventaire);
	}

	public Preparateur getResponsable() {
		return getPreparateur();
	}

	public void setResponsable(Preparateur responsable) {
		setPreparateur(responsable);
	}

	public String getCommentaire() {
		return getRefClient();
	}

	public void setCommentaire(String commentaire) {
		setRefClient(commentaire);
	}

	public List<LigneInventaire> getLignesInventaire() {
		return (ObservableList<LigneInventaire>)(ObservableList<?>)super.getLignes();
	}

	public int getNumLigneMax() {
		return getLignesInventaire().stream().map(LigneInventaire::getNumLigne).max(Integer::compareTo).orElse(0);
	}
}
