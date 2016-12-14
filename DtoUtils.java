package cz.pse.agata.commons.dto;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.vaadin.ui.UI;


public abstract class DtoUtils {

	private static final Logger log = Logger.getLogger(DtoUtils.class);
	
	private DtoUtils(){}
	
	/**
	 * Returns all texts for given class and all its non-primitive fields for locale from current {@link UI}.
	 * All texts are prefixed by given prefix.
	 * 
	 * @param clazz
	 * @param prefix
	 * @return
	 */
	private static Map<Object, String> getColumnHeaders(Class<?> clazz, String prefix){
		Map<Object, String> headers = new HashMap<Object, String>();
		Locale locale = null;
		if (UI.getCurrent() != null) {
			locale = UI.getCurrent().getLocale();
		}
		log.debug("locale = " + locale);
		ResourceBundle bundle = null;
		try {
			bundle = ResourceBundle.getBundle(clazz.getName(), locale);
		} catch (java.lang.NullPointerException e1) {
			log.warn(e1);
			bundle = null;
		} catch (java.util.MissingResourceException e2) {
			log.warn(e2);
			bundle = null;
		}
		if (bundle == null) {
			return headers;
		}
		for (String key : bundle.keySet()) {
			headers.put(StringUtils.trimToEmpty(prefix) + key, bundle.getString(key));
		}
		for(Field field: clazz.getDeclaredFields()){
			if (!field.getType().isPrimitive() && !field.getType().equals(clazz)){
				for (Entry<Object, String> header: getColumnHeaders(
						(Class<?>) field.getType(), StringUtils.trimToEmpty(prefix) + field.getName() + ".").entrySet()){
					if (!headers.containsKey(header.getKey())) {//do not override 
						headers.put(header.getKey(), header.getValue());
					}
				}
			}
		}
		return headers;
	}

	/**
	 * Returns all texts for given class and all its non-primitive fields for locale from current {@link UI}
	 * 
	 * @param clazz
	 * @return
	 */
	public static Map<Object, String> getColumnHeaders(Class<?> clazz){
		return getColumnHeaders(clazz, null);
	}	
	
	/**
	 * Returns texts for given properties of given class
	 * 
	 * @param visibleColumns
	 * @param type
	 * @return
	 */
	public static String[] getColumnHeaders(String[] visibleColumns, Class<?> type){
		Map<Object, String> props = DtoUtils.getColumnHeaders(type);
		List<String> headers = new ArrayList<>();
		for (String column : visibleColumns) {
			headers.add(props.get(column));
		}
		return headers.toArray(new String[headers.size()]);
	}
	

}
