package net.osmand.plus.osmedit;

import java.util.List;

import android.support.v4.app.Fragment;
import net.osmand.access.AccessibleToast;
import net.osmand.data.Amenity;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuAdapter.OnContextMenuClick;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.activities.AvailableGPXFragment;
import net.osmand.plus.activities.AvailableGPXFragment.GpxInfo;
import net.osmand.plus.activities.EnumAdapter;
import net.osmand.plus.activities.EnumAdapter.IEnumWithResource;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.util.Algorithms;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;


public class OsmEditingPlugin extends OsmandPlugin {
	private static final String ID = "osm.editing";
	private OsmandSettings settings;
	private OsmandApplication app;
	
	@Override
	public String getId() {
		return ID;
	}
	
	public OsmEditingPlugin(OsmandApplication app) {
		this.app = app;
	}
	
	@Override
	public boolean init(OsmandApplication app, Activity activity) {
		settings = app.getSettings();
		return true;
	}
	
	private OsmBugsLayer osmBugsLayer;
	private EditingPOIActivity poiActions;
	
	@Override
	public void updateLayers(OsmandMapTileView mapView, MapActivity activity){
		if (osmBugsLayer == null) {
			registerLayers(activity);
		}
		if(mapView.getLayers().contains(osmBugsLayer) != settings.SHOW_OSM_BUGS.get()){
			if(settings.SHOW_OSM_BUGS.get()){
				mapView.addLayer(osmBugsLayer, 2);
			} else {
				mapView.removeLayer(osmBugsLayer);
			}
		}
	}
	
	@Override
	public void registerLayers(MapActivity activity){
		osmBugsLayer = new OsmBugsLayer(activity);
	}
	
	public OsmBugsLayer getBugsLayer(MapActivity activity) {
		if(osmBugsLayer == null) {
			registerLayers(activity);
		}
		return osmBugsLayer;
	}

	@Override
	public void mapActivityCreate(MapActivity activity) {
		// Always create new actions !
		poiActions = new EditingPOIActivity(activity);
		activity.addDialogProvider(getPoiActions(activity));
		activity.addDialogProvider(getBugsLayer(activity));
	}
	
	
	@Override
	public Class<? extends Activity> getSettingsActivity() {
		return SettingsOsmEditingActivity.class;
	}
	
	public EditingPOIActivity getPoiActions(MapActivity activity) {
		if(poiActions == null) {
			poiActions = new EditingPOIActivity(activity);
		}
		return poiActions;
	}
	
	@Override
	public void registerMapContextMenuActions(final MapActivity mapActivity, final double latitude, final double longitude, ContextMenuAdapter adapter,
			final Object selectedObj) {
		OnContextMenuClick listener = new OnContextMenuClick() {
			@Override
			public boolean onContextMenuClick(ArrayAdapter<?> adapter, int resId, int pos, boolean isChecked) {
				if (resId == R.string.context_menu_item_create_poi) {
					getPoiActions(mapActivity).showCreateDialog(latitude, longitude);
				} else if (resId == R.string.context_menu_item_open_bug) {
					if(osmBugsLayer == null) {
						registerLayers(mapActivity);
					}
					osmBugsLayer.openBug(latitude, longitude);
				} else if (resId == R.string.poi_context_menu_delete) {
					getPoiActions(mapActivity).showDeleteDialog((Amenity) selectedObj);
				} else if (resId == R.string.poi_context_menu_modify) {
					getPoiActions(mapActivity).showEditDialog((Amenity) selectedObj);
				}
				return true;
			}
		};
		if(selectedObj instanceof Amenity) {
			adapter.item(R.string.poi_context_menu_modify).icons(R.drawable.ic_action_edit_dark, 
					R.drawable.ic_action_edit_light).listen(listener).position(1).reg();
			adapter.item(R.string.poi_context_menu_delete).icons(R.drawable.ic_action_delete_dark, 
					R.drawable.ic_action_delete_light).listen(listener).position(2).reg();
		} else {
			adapter.item(R.string.context_menu_item_create_poi).icons(R.drawable.ic_action_plus_dark, 
					R.drawable.ic_action_plus_light).listen(listener).position(-1).reg();
		}
		adapter.item(R.string.context_menu_item_open_bug).icons(R.drawable.ic_action_bug_dark, 
				R.drawable.ic_action_bug_light).listen(listener).reg();
	}

	@Override
	public void registerLayerContextMenuActions(OsmandMapTileView mapView, ContextMenuAdapter adapter, MapActivity mapActivity) {
		adapter.item(R.string.layer_osm_bugs).selected(settings.SHOW_OSM_BUGS.get() ? 1 : 0)
				.icons(R.drawable.ic_action_bug_dark, R.drawable.ic_action_bug_light).listen(new OnContextMenuClick() {

					@Override
					public boolean onContextMenuClick(ArrayAdapter<?> adapter, int itemId, int pos, boolean isChecked) {
						if (itemId == R.string.layer_osm_bugs) {
							settings.SHOW_OSM_BUGS.set(isChecked);
						}
						return true;
					}
				}).position(16).reg();

	}

	@Override
	public String getDescription() {
		return app.getString(R.string.osm_editing_plugin_description);
	}
	
	@Override
	public void contextMenuFragment(final Activity la, final Fragment fragment, final Object info, ContextMenuAdapter adapter) {
		if (fragment instanceof AvailableGPXFragment) {
			adapter.item(R.string.local_index_mi_upload_gpx)
					.icons(R.drawable.ic_action_gup_dark, R.drawable.ic_action_gup_light)
					.listen(new OnContextMenuClick() {

						@Override
						public boolean onContextMenuClick(ArrayAdapter<?> adapter, int itemId, int pos, boolean isChecked) {
							sendGPXFiles(la, (AvailableGPXFragment) fragment, (GpxInfo) info);
							return true;
						}
					}).reg();
		}
	}
	
	@Override
	public void optionsMenuFragment(final Activity activity, final Fragment fragment, ContextMenuAdapter optionsMenuAdapter) {
		if (fragment instanceof AvailableGPXFragment) {
			final AvailableGPXFragment f = ((AvailableGPXFragment) fragment);
			optionsMenuAdapter.item(R.string.local_index_mi_upload_gpx)
					.icon(R.drawable.ic_action_gup_dark)
					.listen(new OnContextMenuClick() {

						@Override
						public boolean onContextMenuClick(ArrayAdapter<?> adapter, int itemId, int pos, boolean isChecked) {
							f.openSelectionMode(R.string.local_index_mi_upload_gpx, R.drawable.ic_action_gup_dark,
									R.drawable.ic_action_gup_dark, new OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
											List<GpxInfo> selectedItems = f.getSelectedItems();
											sendGPXFiles(activity, f,
													selectedItems.toArray(new GpxInfo[selectedItems.size()]));
										}
									});
							return true;
						}
					}).position(5).reg();
		}
	}
	
	public enum UploadVisibility implements IEnumWithResource {
		Public(R.string.gpxup_public),
		Identifiable(R.string.gpxup_identifiable),
		Trackable(R.string.gpxup_trackable),
		Private(R.string.gpxup_private);
		private final int resourceId;

		private UploadVisibility(int resourceId) {
			this.resourceId = resourceId;
		}
		public String asURLparam() {
			return name().toLowerCase();
		}
		@Override
		public int stringResource() {
			return resourceId;
		}
	}
	
	public boolean sendGPXFiles(final Activity la, AvailableGPXFragment f, final GpxInfo... info){
		String name = settings.USER_NAME.get();
		String pwd = settings.USER_PASSWORD.get();
		if(Algorithms.isEmpty(name) || Algorithms.isEmpty(pwd)){
			AccessibleToast.makeText(la, R.string.validate_gpx_upload_name_pwd, Toast.LENGTH_LONG).show();
			return false;
		}
		Builder bldr = new AlertDialog.Builder(la);
		LayoutInflater inflater = (LayoutInflater)la.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = inflater.inflate(R.layout.send_gpx_osm, null);
		final EditText descr = (EditText) view.findViewById(R.id.memory_size);
		if(info.length > 0 && info[0].getFileName() != null) {
			int dt = info[0].getFileName().indexOf('.');
			descr.setText(info[0].getFileName().substring(0, dt));
		}
		final EditText tags = (EditText) view.findViewById(R.id.TagsText);		
		final Spinner visibility = ((Spinner)view.findViewById(R.id.Visibility));
		EnumAdapter<UploadVisibility> adapter = new EnumAdapter<UploadVisibility>(la, android.R.layout.simple_spinner_item, UploadVisibility.values());
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		visibility.setAdapter(adapter);
		visibility.setSelection(0);
		
		bldr.setView(view);
		bldr.setNegativeButton(R.string.default_buttons_no, null);
		bldr.setPositiveButton(R.string.default_buttons_yes, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				new UploadGPXFilesTask(la, descr.getText().toString(), tags.getText().toString(), 
				 (UploadVisibility) visibility.getItemAtPosition(visibility.getSelectedItemPosition())
					).execute(info);
			}
		});
		bldr.show();
		return true;
	}
	

	@Override
	public String getName() {
		return app.getString(R.string.osm_settings);
	}

}
