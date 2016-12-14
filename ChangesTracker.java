package cz.pse.agata.commons.model;

import java.util.Collection;

import cz.pse.agata.commons.ui.view.component.Change;

/**
 *Interface for tracking changes on object of type <T>
 *
 * @param <T>
 */
public interface ChangesTracker<T> {

	/**
	 * @return all changes on tracked object of type <T>
	 */
	Collection<Change<T>> getChanges();
}
