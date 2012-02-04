package jp.android.takamichie.NFCCollector;

import java.util.ArrayList;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

public class ItemAdapter extends ArrayAdapter<Item> {

    private static final String LOG_TAG = ItemAdapter.class.getSimpleName();
    private ArrayList<Item> items;
    private LayoutInflater inflater;
    private int resId;

    public ItemAdapter(Context context, int textViewResourceId,
	    ArrayList<Item> objects) {
	super(context, textViewResourceId, objects);
	this.items = objects;
	this.inflater = (LayoutInflater) context
		.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	this.resId = textViewResourceId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
	Log.v(LOG_TAG, "getView Start:pos" + position);
	View view = convertView;
	if (view == null) {
	    // 受け取ったビューがnullなら新しくビューを生成
	    view = inflater.inflate(resId, null);
	}
	Item item = items.get(position);
	if (item != null) {
	    // Log.v(LOG_TAG, "ItemData Refresh");
	    TextView labelItemName = (TextView) view
		    .findViewById(R.id.labelItemName);
	    TextView labelItemId = (TextView) view
		    .findViewById(R.id.labelItemId);
	    CheckBox checkItemHas = (CheckBox) view
		    .findViewById(R.id.checkItemHas);

	    labelItemName.setText(item.getItemName());
	    labelItemId.setText(item.idToString());
	    checkItemHas.setChecked(item.isHas());
	    /*
	     * Log.v(LOG_TAG, item.getItemName()); Log.v(LOG_TAG,
	     * item.idToString()); Log.v(LOG_TAG, "" + item.isHas());
	     */
	}
	return view;

    }
}
