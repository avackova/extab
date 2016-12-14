package cz.pse.agata.commons.ui.field;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;

import com.vaadin.data.Container;
import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.ui.AbstractField;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomField;
import com.vaadin.ui.Field;
import com.vaadin.ui.Table;
import com.vaadin.ui.themes.Reindeer;

import cz.pse.agata.commons.dto.DtoUtils;
import cz.pse.agata.commons.model.ChangesTracker;
import cz.pse.agata.commons.model.PxFieldFactory;
import cz.pse.agata.commons.model.Utils;
import cz.pse.agata.commons.ui.view.DTOFieldFormatter;
import cz.pse.agata.commons.ui.view.component.Change;
import cz.px.iis.ng.ui.table.column.IPxDTOFieldFormatter;

/**
 *Table with additional column for removing elements (rows). In the last row (in editable mode), there 
 *is + button for adding a new element (row). It can track changes: changed fields change background
 *color and all changes are available as collection of changes or a table of changes.
 *
 * @param <T>
 */
public class ExtTable<T> extends Table implements ChangesTracker<T>{

	private static final long serialVersionUID = 5340010844285857433L;

	private static final Logger log = Logger.getLogger(ExtTable.class);

	protected static final String DELETE_COLUMN_ID = "delete";
	
	protected T fakeData;
	
	protected PlusButton plusButton;

	private final Class<? super T> beanType;
	private boolean trackChanges;
	private Object[] visibleColumns;
	private String[] columnHeaders;
	
	private Set<String> requiredFields = new HashSet<>();
	
	private final Map<Object, List<Change>> changes = new HashMap<>();
	
	IPxDTOFieldFormatter formatter;
	
	public ExtTable(Class<? super T> class1, Object[] visibleColumns, boolean trackChanges) {
		super();
		this.beanType = class1;
		this.visibleColumns = visibleColumns;
		this.trackChanges = trackChanges;
		createContent();
	}
	
	public ExtTable(Class<? super T> class1, Object[] objects){
		this(class1, objects, false);
	}

	public ExtTable(Class<T> beanType, String[] visibleColumns, String caption, boolean trackChanges) {
		super(caption);
		this.beanType = beanType;
		this.visibleColumns = visibleColumns;
		this.trackChanges = trackChanges;
		createContent();
	}
	
	public ExtTable(Class<T> beanType, String[] visibleColumns, String caption){
		this(beanType, visibleColumns, caption, false);
	}

	public ExtTable(String caption, String[] visibleColumns, BeanItemContainer<T> dataSource, boolean trackChanges) {
		super(caption, dataSource);
		this.beanType = dataSource.getBeanType();
		this.visibleColumns = visibleColumns;
		this.trackChanges = trackChanges;
		createContent();
	}
	
	public ExtTable(String caption, String[] visibleColumns, BeanItemContainer<T> dataSource){
		this(caption, visibleColumns, dataSource, false);
	}
	
	public boolean isTrackChanges() {
		return trackChanges;
	}
	
	public void setTrackChanges(boolean trackChanges) {
		this.trackChanges = trackChanges;
		if (trackChanges){
			storeValues(getContainerDataSource());
		}else{
			changes.clear();
		}
	}
	
	@Override
	protected String formatPropertyValue(Object rowId, Object colId, Property<?> property) {
		String formattedValue = formatter.formatFieldValue((String) colId, property.getValue());
		if (formattedValue == null) {
			formattedValue = Objects.toString(property.getValue(), null);
		}
		return formattedValue;
	}
	
	/**
	 * @param editable
	 * @return visible columns, if table is in <i>editable</i> editable mode, the last column is 
	 * 	special <code>delete column</code> 
	 */
	private String[] visibleColumns(boolean editable){
		String[] visible = new String[editable ? visibleColumns.length + 1 : visibleColumns.length];
		for (int i = 0; i < visibleColumns.length; i++) {
			visible[i] = Objects.toString(visibleColumns[i]);
		}
		if (editable) {
			visible[visible.length - 1] = DELETE_COLUMN_ID;
		}
		return visible;
	}
	
	/**
	 * Configures field in table. If tracking changes adds all needed listener to remember and display changes.
	 * 
	 * @param field
	 * @param itemId
	 * @param propertyId
	 */
	private void configureField(Field<?> field, Object itemId, Object propertyId){
		field.setRequired(requiredFields.contains(propertyId));
		if (trackChanges && field.isAttached()) {
			List<Change> values = changes.get(itemId);
			Change currentChange = null;
			Object oldValue = null;
			if (values != null) {
				for (Change change : values) {
					if (propertyId.equals(change.getPropertyName())) {
						currentChange = change;
						break;
					}
				}
				oldValue = currentChange.getOldValue();
			}
			if (currentChange != null && !Objects.equals(currentChange.getNewValue(), oldValue)){
				field.addStyleName("changed-field");
				String formattedValue = formatter.formatFieldValue((String) propertyId, oldValue);
				if (formattedValue == null) {
					formattedValue = Objects.toString(oldValue, null);
				}
				((AbstractField<?>) field).setDescription(MessageFormat.format(Utils.getMessage(
						"TOOLTIP", cz.pse.agata.commons.ui.view.component.ChangesTable.class), formattedValue != null ? formattedValue : Objects.toString(oldValue, "")));
			}else{
				field.removeStyleName("changed-field");
				((AbstractField<?>) field).setDescription(null);
			}
		}
		field.addValueChangeListener(event -> {
			if (!((AbstractField) event.getProperty()).isAttached()){
				return;
			}
			if (trackChanges) {
				Object newValue = ((AbstractField<Object>) event.getProperty()).getConvertedValue();
				List<Change> values = changes.get(itemId);
				if (values == null) {
					log.warn("No changes value for item id \"" + itemId + "\".");
					return;
				}
				Change currentChange = null;
				Object oldValue = null;
				for (Change change : values) {
					if (propertyId.equals(change.getPropertyName())){
						currentChange = change;
						break;
					}
				}
				oldValue = currentChange.getOldValue();
				if (!Objects.equals(newValue, oldValue)){
					currentChange.setNewValue(newValue);
					field.addStyleName("changed-field");
					String formattedValue = formatter.formatFieldValue((String) propertyId, oldValue);
					if (formattedValue == null) {
						formattedValue = Objects.toString(oldValue, null);
					}
					((AbstractField<?>) field).setDescription(MessageFormat.format(Utils.getMessage(
							"TOOLTIP", cz.pse.agata.commons.ui.view.component.ChangesTable.class), formattedValue != null ? formattedValue : Objects.toString(oldValue, "")));
				}else{
					field.removeStyleName("changed-field");
					((AbstractField<?>) field).setDescription(null);
				}
			}
			ExtTable.this.fireValueChange(true);
		});
	}
	
	private void createContent(){
		formatter = new DTOFieldFormatter(beanType);
		Map<Object, String> headersMap = DtoUtils.getColumnHeaders(beanType);
		columnHeaders = new String[visibleColumns.length];
		for (int i = 0; i < columnHeaders.length; i++) {
			columnHeaders[i] = headersMap.get(visibleColumns[i]);
		}
		fakeData = createNewItem();
		plusButton = new PlusButton();
		setTableFieldFactory(new PxFieldFactory());
		setSizeFull();
		setContainerDataSource(new BeanItemContainer<>(beanType));
		addItemSetChangeListener(event -> {
			fireValueChange(true);
		});
		addGeneratedColumn(DELETE_COLUMN_ID, (source, itemId, columnId) -> {
			Button minus = new Button("-");
			minus.addStyleName(Reindeer.BUTTON_SMALL);
			minus.addClickListener(event -> {
				source.removeItem(itemId);
			}); 
			return itemId == fakeData ? null : minus;
		});
	}
	
	@Override
	protected Object getPropertyValue(Object rowId, Object colId, Property property) {
		Object propertyValue = rowId == fakeData ? (colId == getVisibleColumns()[0] ? plusButton : "") 
				: super.getPropertyValue(rowId, colId, property);
		if (propertyValue instanceof Field){
			configureField((Field<?>) propertyValue, rowId, colId);
		}
		return propertyValue;
	}
	
	@Override
	public Item addItem(Object itemId) throws UnsupportedOperationException {
		//button should stay last or first
		int fIndex = indexOfId(fakeData);
		if (fIndex < 0) {//no fake data in table
			if (itemId != fakeData || getSortContainerPropertyId() == null || isSortAscending()) {
				return super.addItem(itemId);
			}
			//adding button and sorted descending
			return ((BeanItemContainer<T>) getContainerDataSource()).addItemAt(0, itemId );
		}
		if (fIndex == 0) {//button is first -> put after button
			return ((BeanItemContainer<T>) getContainerDataSource()).addItemAt(1, itemId );
		}else {//button is last -> put before button
			return ((BeanItemContainer<T>) getContainerDataSource()).addItemAt(fIndex, itemId );
		}
	}
	
	/**
	 * Adds empty row to the table
	 */
	public void addNewItem() {
		plusButton.click();
	}

	@Override
	public void setVisibleColumns(Object... visibleColumns) {
		this.visibleColumns = new String[visibleColumns.length];
		for (int i = 0; i < visibleColumns.length; i++) {
			this.visibleColumns[i] = Objects.toString(visibleColumns[i]);
		}
		super.setVisibleColumns(visibleColumns(isEditable()));
	}
	
	public void setRequiredFields(String... fieldId){
		requiredFields.clear();
		for (String string : fieldId) {
			requiredFields.add(string);
		}
	}
	
	public void addRequiredField(String fieldId){
		requiredFields.add(fieldId);
	}
	
	public boolean removeRequiredField(String fieldId){
		return requiredFields.remove(fieldId);
	}
	
	/**
	 * Prepares this object from given container
	 * 
	 * @param container
	 */
	private void storeValues(Container container){
		changes.clear();
		for (Object id : container.getItemIds()) {
			if (id == fakeData) {
				continue;
			}
			Item item = container.getItem(id);
			List<Change> values = new ArrayList<Change>();
			for (Object propId : item.getItemPropertyIds()) {
				Object value = item.getItemProperty(propId).getValue();
				values.add(new Change<>(propId, value, value));
			}
			changes.put(id, values);
		}
		addItemSetChangeListener(event -> {
			for (Object id : event.getContainer().getItemIds()) {
				if (id == fakeData) {
					continue;
				}
				Item item = event.getContainer().getItem(id);
				List<Change> values = changes.get(id);
				if (values != null) {
					for (Object propId : item.getItemPropertyIds()) {
						Object value = item.getItemProperty(propId).getValue();
						boolean found = false;
						for (Change change : values) {
							if (propId.equals(change.getPropertyName())) {
								change.setNewValue(value);
								break;
							}
						}
					} 
				}else{//values == null -> new item
					values = new ArrayList<Change>();
					for (Object propId : item.getItemPropertyIds()) {
						Object value = item.getItemProperty(propId).getValue();
						values.add(new Change<>(propId, null, value));
					}
					changes.put(id, values);
				}
			}
		});
	}
	
	@Override
	public void setContainerDataSource(Container newDataSource) {
		if (visibleColumns == null) {//we are in super.constructor 
			super.setContainerDataSource(newDataSource);
			if (trackChanges) {
				storeValues(newDataSource);
			}
			return;
		}
		setContainerDataSource(newDataSource, Arrays.asList(visibleColumns));
		if (trackChanges) {
			storeValues(newDataSource);
		}
		String[] headers = new String[isEditable() ? visibleColumns.length + 1 : visibleColumns.length];
		for (int i = 0; i < visibleColumns.length; i++) {
			headers[i] = columnHeaders[i];
		}
		if (isEditable()) {
			headers[headers.length - 1] = "";
		}
		setColumnHeaders(headers);
		if (isEditable()) {
			addItem(fakeData);
		}
	}
	
	@Override
	public void sort(Object[] propertyId, boolean[] ascending) throws UnsupportedOperationException {
		if (isEditable()) {
			removeItem(fakeData);
		}
		super.sort(propertyId, ascending);
		if (isEditable()) {
			addItem(fakeData);
		}
	}
	
	@Override
	public void setEditable(boolean editable) {
		super.setEditable(editable);
		if (editable){
			addItem(fakeData);
		}else{
			removeItem(fakeData);
		}
		setVisibleColumns(visibleColumns);
	}
	
	@Override
	public Collection<?> getItemIds() {
		Collection<?> allItems = super.getItemIds();
		if (!isEditable()) {
			return allItems;
		}
		//do not return fake item
		ArrayList<?> copy = new ArrayList<>(allItems);
		copy.remove(fakeData);
		return Collections.unmodifiableCollection(copy);
	}

	@Override
	public Collection<Change<T>> getChanges(){
		Collection<Change<T>> changes = new ArrayList<>();
		for (Object id : getItemIds()) {
			List<Change> values = this.changes.get(id);
			if (values == null) {
				continue;
			}
			for (Change change : values) {
				if (!Objects.equals(change.getNewValue(), change.getOldValue())){
					changes.add(change);
				}
			}
		}
		//removed
		for (Object id : this.changes.keySet()) {
			if (!(getContainerDataSource().containsId(id))){
				for (Change change : this.changes.get(id)) {
					if (Arrays.asList(getVisibleColumns()).contains(change.getPropertyName())){
						change.setNewValue(null);
						changes.add(change);
					}
				}
			}
		}
		return changes;
	}
	
	public ExtTable<T> getChangesTable(){
		return new ChangesTable(this, changes);
	}
	
	public Class<? super T> getBeanType() {
		return beanType;
	}
	
	protected T createNewItem(){
		try {
			return (T) beanType.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	final class PlusButton extends CustomField<String> {
		
		private static final long serialVersionUID = 7140156080879104267L;
		
		private Button plus;
	
		@Override
		protected Component initContent() {
			plus = new Button("+");
			plus.addStyleName(Reindeer.BUTTON_SMALL);
			plus.addClickListener(new ClickListener() {
				
				@Override
				public void buttonClick(ClickEvent event) {
					T data = createNewItem();
					//button should stay last or first
					int fIndex = indexOfId(fakeData);
					if (fIndex == 0 && CollectionUtils.isNotEmpty(getItemIds())) {//button is first, but not sole ->
						((BeanItemContainer<T>) getContainerDataSource()).addItemAt(1, data );
					}else {//button is last -> put before button
						((BeanItemContainer<T>) getContainerDataSource()).addItemAt(fIndex, data );
					}
				}
			});
			return plus;
		}
	
		public void click(){
			getContent();
			plus.click();
		}
		
		@Override
		public Class<? extends String> getType() {
			return String.class;
		}
	}


	public void clearAllChanges() {
		changes.clear();
		for (Object id : getItemIds()) {
			Item item = getItem(id);
			List<Change> values = new ArrayList<Change>();
			for (Object propId : item.getItemPropertyIds()) {
				Object value = item.getItemProperty(propId).getValue();
				values.add(new Change<>(propId, value, value));
			}
			changes.put(id, values);
		}
		refreshRowCache();
	}
}

/**
 *Table showing changes 
 *
 * @param <T>
 */
class ChangesTable<T> extends ExtTable<T> {
	
	private final Map<Object, List<Change>> oChanges;
	private final ExtTable<T> originalTable;

	private Container originalContainer;

	ChangesTable(ExtTable<T> original, final Map<Object, List<Change>> changes) {
		super(original.getBeanType(), original.getVisibleColumns());
		this.originalTable = original;
		this.oChanges = changes;
		setContainerDataSource(original.getContainerDataSource());
		setEditable(false);
		addAttachListener(event -> {
			refreshRowCache();
		});
		setCellStyleGenerator((source, itemId, propertyId) -> {
			List<Change> change = changes.get(itemId);
			if (originalContainer.getItem(itemId) == null) {//deleted
				return "line-through";
			}
			if (change == null) {
				return "inactive";
			}
			for (Change change2 : change) {
				if (change2.getPropertyName().equals(propertyId)){
					return Objects.equals(change2.getNewValue(), change2.getOldValue()) ? "inactive" //no change
							: null;	
				}
			}
			return null;
		});
	}
	
	@Override
	public void setEditable(boolean editable) {
		if (editable) {
			throw new IllegalArgumentException("Changes table can't be editable");
		}
		super.setEditable(editable);
	}
	
	@Override
	public void setVisibleColumns(Object... visibleColumns) {
		Object[] vc;
		if (visibleColumns != null && visibleColumns.length > 0 
				&& visibleColumns[visibleColumns.length - 1].equals(DELETE_COLUMN_ID)){
			vc = ArrayUtils.subarray(visibleColumns, 0, visibleColumns.length - 1);
		}else{
			vc = visibleColumns;
		}
		super.setVisibleColumns(vc);
	}
	
	@Override
	public void setContainerDataSource(Container newDataSource) {
		originalContainer = newDataSource;
		if (getBeanType() == null || oChanges == null) {//still in constructor
			return;
		}
		Container container = new BeanItemContainer<>(getBeanType());
		for (Object id : newDataSource.getItemIds()) {
			if (id != originalTable.fakeData) {
				container.addItem(id);
			}
		}
		for (Object id : oChanges.keySet()) {
			container.addItem(id);
		}
		if (originalContainer instanceof ItemSetChangeNotifier){
			((ItemSetChangeNotifier) originalContainer).addItemSetChangeListener(new ItemSetChangeListener() {
				@Override
				public void containerItemSetChange(com.vaadin.data.Container.ItemSetChangeEvent event) {
					for (Object id : originalTable.getItemIds()) {
						addItem(id);
					}
				}
			});
		}
		super.setContainerDataSource(container);
	}
	
	@Override
	protected String formatPropertyValue(Object rowId, Object colId, Property<?> property) {
		List<Change> change = oChanges.get(rowId);
		if (change == null || originalContainer.getItem(rowId) == null) {//no change or deleted
			return super.formatPropertyValue(rowId, colId, property);
		}
		for (Change change2 : change) {
			if (colId.equals(change2.getPropertyName())){
				String oldValue = null;
				if (change2.getOldValue() != null) {
					oldValue = formatter.formatFieldValue((String) colId, change2.getOldValue());
					if (oldValue == null) {
						oldValue = Objects.toString(property.getValue(), null);
					} 
				}
				String newValue = null;
				if (change2.getNewValue() != null) {
					newValue = formatter.formatFieldValue((String) colId, change2.getNewValue());
					if (newValue == null) {
						newValue = Objects.toString(property.getValue(), null);
					} 
				}
				return Objects.equals(change2.getNewValue(), change2.getOldValue()) ? super.formatPropertyValue(rowId, colId, property) //no change
						: oldValue + " -> " + newValue;	
			}
		}
 		return super.formatPropertyValue(rowId, colId, property);
	}
	
	@Override
	public Collection<Change<T>> getChanges() {
		return originalTable.getChanges();
	}
	
	@Override
	public ExtTable<T> getChangesTable() {
		return this;
	}
}