package jp.android.takamichie.NFCCollector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

public class NFCCollectorActivity extends Activity {

    private static final String LOG_TAG = NFCCollectorActivity.class
	    .getSimpleName();
    private String[][] mTechLists;
    private IntentFilter[] mFilters;
    private PendingIntent mPendingIntent;
    private NfcAdapter mNFCAdapter;
    private ItemAdapter mListItemAdapter;
    private ArrayAdapter<String> mCollectionsSpinnerAdapter;
    private String mCurrentCollection;
    private boolean mSaved;
    private boolean refreshing;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
	Log.d(LOG_TAG, "OnCreate Start");
	super.onCreate(savedInstanceState);
	setContentView(R.layout.main);

	//
	// ActionBar
	//
	View v = getLayoutInflater().inflate(R.layout.actionbar, null);
	ActionBar bar = getActionBar();
	bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
	bar.setCustomView(v);
	bar.setDisplayShowCustomEnabled(true);
	bar.setDisplayShowHomeEnabled(true);
	bar.setDisplayShowTitleEnabled(true);

	//
	// listItems
	//
	mListItemAdapter = new ItemAdapter(this, R.layout.list,
		new ArrayList<Item>());
	ListView listItems = (ListView) findViewById(R.id.listItems);
	listItems.setAdapter(mListItemAdapter);

	//
	// spinnerCollections
	//
	mCollectionsSpinnerAdapter = new ArrayAdapter<String>(this,
		android.R.layout.simple_spinner_dropdown_item);
	Spinner spinnerCollections = (Spinner) v
		.findViewById(R.id.spinnerCollections);
	spinnerCollections
		.setOnItemSelectedListener(new SpinnerCollections_OnItemSelected());
	spinnerCollections.setAdapter(mCollectionsSpinnerAdapter);

	//
	// NFCの設定準備
	//
	NfcManager nfcManager = (NfcManager) getSystemService(NFC_SERVICE);
	mNFCAdapter = nfcManager.getDefaultAdapter();

	Intent intent = new Intent(this, getClass());
	intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
	mPendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

	IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
	try {
	    ndef.addDataType("*/*");
	} catch (MalformedMimeTypeException e) {
	    throw new RuntimeException(e);
	}
	mFilters = new IntentFilter[] { ndef, };

	mTechLists = new String[][] { new String[] { NfcF.class.getName() },
		new String[] { NfcA.class.getName() } };

	refreshCollections();
	loadSettings();
    }

    /**
     * Dispatchの解除と、設定の永続化。
     */
    @Override
    protected void onPause() {
	Log.d(LOG_TAG, "OnPause Start");
	super.onPause();
	if (mNFCAdapter != null) {
	    mNFCAdapter.disableForegroundDispatch(this);
	    Log.i(LOG_TAG, "Disabled Foreground Dispatch");
	}
	// 設定の永続化
	Log.i(LOG_TAG, "Save Preference");
	Log.i(LOG_TAG, Constants.PREFERENCE_CURRENT_COLLECTION + "="
		+ mCurrentCollection);
	SharedPreferences pref = PreferenceManager
		.getDefaultSharedPreferences(getApplicationContext());
	Editor editor = pref.edit();
	editor.putString(Constants.PREFERENCE_CURRENT_COLLECTION,
		mCurrentCollection);
	editor.commit();
    }

    /**
     * Dispatchの設定。
     */
    @Override
    protected void onResume() {
	Log.d(LOG_TAG, "OnResume Start");
	super.onResume();
	if (mNFCAdapter != null) {
	    mNFCAdapter.enableForegroundDispatch(this, mPendingIntent,
		    mFilters, mTechLists);
	    Log.i(LOG_TAG, "Enabled Foreground Dispatch");
	}
    }

    /**
     * 新規インテントの取得。 NFCタグをスキャンした際にこのメソッドが呼び出されます。
     */
    @Override
    protected void onNewIntent(Intent intent) {
	Log.d(LOG_TAG, "OnNewIntent Start");
	super.onNewIntent(intent);
	Bundle extra = intent.getExtras();
	if (extra != null) {
	    Item item = new Item(extra.getByteArray(NfcAdapter.EXTRA_ID));
	    setTagID(item);
	}
    }

    /**
     * オプションメニューの初期化。
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	Log.d(LOG_TAG, "OnCreateOptionMenu Start");
	super.onCreateOptionsMenu(menu);
	MenuInflater mi = getMenuInflater();
	mi.inflate(R.menu.main, menu);
	return true;

    }

    /**
     * オプションメニューの選択。
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	Log.d(LOG_TAG, "OnOptionItemSelected Start");
	boolean result = false;
	switch (item.getItemId()) {
	case R.id.menuitemSaveList:
	    menuMethodSaveList();
	    result = true;
	    break;
	case R.id.menuitemDeleteList:
	    menuMethodDeleteList();
	    result = true;
	    break;
	case R.id.menuitemClearCheck:
	    menuMethodClearCheck();
	    result = true;
	    break;
	case R.id.menuitemPreference:
	    menuMethodShowPreference();
	    result = true;
	    break;
	default:
	    break;
	}
	return result;
    }

    /**
     * {@link Item}に応じた処理を実施します。 未登録のIDであれば、アイテムの新規登録を、
     * 登録済みのアイテムであれば、アイテムのチェックを行います。
     *
     * @param item
     *            処理対象となる{@link Item}
     */
    private void setTagID(Item item) {
	Log.d(LOG_TAG, "setTagID Start");
	// IDがすでに登録済みか確認。ついでに非チェック項目数の確認
	int count = mListItemAdapter.getCount();
	Item foundItem = null;
	int unchecked = 0;
	for (int i = 0; i < count; i++) {
	    Item checkItem = mListItemAdapter.getItem(i);
	    if (item.getId() == checkItem.getId()) {
		foundItem = checkItem;
	    }else if (!checkItem.isHas()) {
		unchecked++;
	    }
	}
	if (foundItem == null) {
	    // アイテムの新規登録
	    Log.i(LOG_TAG, "No Found. Append New Item");
	    appendID(item);
	} else {
	    // アイテムのチェック
	    Log.i(LOG_TAG, "Found. Item Checked");
	    foundItem.setHas(true);
	    ListView listItems = (ListView) findViewById(R.id.listItems);
	    listItems.invalidateViews();
	    Log.i(LOG_TAG, "Unchecked Count " + unchecked);
	    if (unchecked == 0) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.caption_dialog_finished);
		builder.setMessage(R.string.wording_dialog_finishallcheck);
		builder.setPositiveButton(android.R.string.ok,
			new Dialog_Dismiss());
		builder.create().show();
	    }
	}
    }

    /**
     * 新しい{@link Item}をリストに追加します
     *
     * @param item
     *            追加したい{@link Item}
     */
    private void appendID(final Item item) {
	Log.d(LOG_TAG, "appendID Start");
	String ids = item.idToString();
	Log.i(LOG_TAG, ids);
	final EditText name = new EditText(this);
	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	builder.setTitle(R.string.caption_dialog_appendcard);
	builder.setMessage("ID:" + ids);
	builder.setView(name);
	builder.setPositiveButton(android.R.string.ok,
		new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
			item.setItemName(name.getText().toString());
			mListItemAdapter.add(item);
		    }
		});
	builder.setNegativeButton(android.R.string.cancel, new Dialog_Dismiss());
	builder.create().show();
    }

    /**
     * アプリケーションの設定を読み込みます。
     */
    private void loadSettings() {
	Log.d(LOG_TAG, "loadSettings Start");
	SharedPreferences pref = PreferenceManager
		.getDefaultSharedPreferences(getApplicationContext());
	String m = pref.getString(Constants.PREFERENCE_CURRENT_COLLECTION,
		getDefaultListName());
	Log.d(LOG_TAG, Constants.PREFERENCE_CURRENT_COLLECTION + "=" + m);
	loadList(m);
    }

    /**
     * コレクションを読み込みリストに表示します。
     *
     * @param collection
     *            読み込むコレクション。ファイルが存在しない場合、何も読み込まず、リストをクリアします。
     */
    private void loadList(String collection) {
	Log.d(LOG_TAG, "loadList Start");
	File file = new File(getCollectionDir(), collection);
	if (file.exists()) {
	    Log.i(LOG_TAG, "Load Collections " + file.getAbsolutePath());
	    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
	    dialog.setPositiveButton(android.R.string.ok, new Dialog_Dismiss());

	    try {
		FileInputStream in = new FileInputStream(file);
		Properties prop = new Properties();
		prop.loadFromXML(in);
		Log.i(LOG_TAG, "Loaded.");
		mListItemAdapter.clear();
		int count = Integer.parseInt((String) prop.get("count"));
		for (int i = 0; i < count; i++) {
		    Item saveItem = new Item(Long.parseLong((String) prop.get(i
			    + ".id")));
		    saveItem.setItemName((String) prop.get(i + ".name"));
		    mListItemAdapter.add(saveItem);
		}
		mCurrentCollection = collection;
		// 行を選択する
		View v = getActionBar().getCustomView();
		Spinner spinnerCollections = (Spinner) v
			.findViewById(R.id.spinnerCollections);
		int ccount = spinnerCollections.getCount();
		for (int i = 0; i < ccount; i++) {
		    if (mCurrentCollection.equals(spinnerCollections
			    .getItemAtPosition(i))) {
			spinnerCollections.setSelection(i);
		    }
		}
	    } catch (InvalidPropertiesFormatException e) {
		dialog.setMessage(R.string.wording_dialog_loadfailedInvalidFormat);
		dialog.create().show();
		e.printStackTrace();
	    } catch (IOException e) {
		dialog.setMessage(R.string.wording_dialog_loadfailed);
		dialog.create().show();
		e.printStackTrace();
	    }
	} else {
	    deleteList(null);
	}
    }

    /**
     * リストを実際に保存します。
     *
     * @param collection
     *            保存するリストの名称。
     */
    private void saveList(String collection) {
	Log.d(LOG_TAG, "saveList Start");
	File file = new File(getCollectionDir(), collection);
	Log.i(LOG_TAG, "Save Collections " + file.getAbsolutePath());
	Properties prop = new Properties();
	int count = mListItemAdapter.getCount();
	prop.put("count", Integer.toString(count));
	for (int i = 0; i < count; i++) {
	    Item saveItem = mListItemAdapter.getItem(i);
	    prop.put(i + ".id", Long.toString(saveItem.getId()));
	    prop.put(i + ".name", saveItem.getItemName());
	}
	// 保存処理
	FileOutputStream out = null;
	try {
	    out = new FileOutputStream(file);
	    prop.storeToXML(out, "");
	    mSaved = true;
	    Log.i(LOG_TAG, "Save Success");
	    refreshCollections();
	} catch (IOException e) {
	    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
	    dialog.setMessage(R.string.wording_dialog_savetofailed);
	    dialog.setPositiveButton(android.R.string.ok, new Dialog_Dismiss());
	    dialog.create().show();
	    e.printStackTrace();
	} finally {
	    if (out != null) {
		try {
		    out.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}
	    }
	}
    }

    /**
     * リストを実際に削除します。 もし保存済みのリストを開いていた場合、ファイルごと削除します。
     *
     * @param collection
     *            削除するリストの名称。nullを指定すると、リストデータの消去のみを行います。
     */
    private void deleteList(String collection) {
	Log.d(LOG_TAG, "deleteList Start");
	if (collection != null) {
	    File file = new File(getCollectionDir(), collection);
	    Log.i(LOG_TAG, "Delete File " + file.getAbsolutePath());
	    if (file.exists()) {
		file.delete();
	    }
	}
	mListItemAdapter.clear();
	mSaved = false;
	mCurrentCollection = getDefaultListName();
	refreshCollections();
    }

    /**
     * コレクションの一覧を更新します。
     */
    private void refreshCollections() {
	Log.d(LOG_TAG, "refreshCollections Start");
	Log.d(LOG_TAG, "Current Collection is " + mCurrentCollection);
	refreshing = true;
	File[] files = getCollectionDir().listFiles();
	int i = 0;
	int l = files.length;
	mCollectionsSpinnerAdapter.clear();
	for (File f : files) {
	    mCollectionsSpinnerAdapter.add(f.getName());
	    if (mCurrentCollection == f.getName()) {
		l = i;
	    }
	    i++;
	}
	mCollectionsSpinnerAdapter.add(getDefaultListName());
	View v = getActionBar().getCustomView();
	Spinner spinnerCollections = (Spinner) v
		.findViewById(R.id.spinnerCollections);
	spinnerCollections.setSelection(l);
	refreshing = false;
    }

    /**
     * コレクションの設定保存ディレクトリを示す{@link File}を取得します。
     *
     * @return コレクションの設定保存ディレクトリを示す{@link File}
     */
    private File getCollectionDir() {
	return getApplication().getDir("collections", MODE_WORLD_READABLE);
    }

    /**
     * 未保存時のデフォルトコレクションファイル名を取得します。
     *
     * @return デフォルトコレクションファイル名
     */
    private String getDefaultListName() {
	return getString(R.string.newListName);
    }

    // 以下メニュー用処理

    /**
     * 「リスト保存」メニュー。
     */
    private void menuMethodSaveList() {
	if (mSaved) {
	    saveList(mCurrentCollection);
	} else {
	    final EditText name = new EditText(this);
	    name.setText(mCurrentCollection);
	    name.setSelectAllOnFocus(true);
	    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
	    dialog.setMessage(R.string.wording_dialog_queryListSaved);
	    dialog.setView(name);
	    dialog.setPositiveButton(android.R.string.yes,
		    new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			    saveList(name.getText().toString());
			}
		    });
	    dialog.setNegativeButton(android.R.string.no, new Dialog_Dismiss());
	    dialog.create().show();
	}
    }

    /**
     * 「リスト削除」メニュー。
     */
    private void menuMethodDeleteList() {
	AlertDialog.Builder dialog = new AlertDialog.Builder(this);
	dialog.setMessage(String.format(
		getString(R.string.wording_dialog_deletecollection),
		mCurrentCollection));
	dialog.setPositiveButton(android.R.string.yes,
		new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
			deleteList(mCurrentCollection);
		    }
		});
	dialog.setNegativeButton(android.R.string.no, new Dialog_Dismiss());
	dialog.create().show();
    }

    /**
     * 「チェックのクリア」メニュー。
     */
    private void menuMethodClearCheck() {
	int count = mListItemAdapter.getCount();
	for (int i = 0; i < count; i++) {
	    mListItemAdapter.getItem(i).setHas(false);
	}
	ListView listItems = (ListView) findViewById(R.id.listItems);
	listItems.invalidateViews();

    }

    /**
     * 「設定」メニュー。
     */
    private void menuMethodShowPreference() {
    }

    /**
     * コレクション一覧表示スピナーの選択時イベントに応答する{@link AdapterView.OnItemSelectedListener}
     */
    private final class SpinnerCollections_OnItemSelected implements
	    AdapterView.OnItemSelectedListener {
	@Override
	public void onItemSelected(AdapterView<?> parent, View view,
		int position, long id) {
	    if (!refreshing) {
		Log.d(LOG_TAG,
			"SpinnerCollections_OnItemSelected#onItemSelected Start");
		String n = mCollectionsSpinnerAdapter.getItem(position);
		Log.d(LOG_TAG, "Select " + n);
		loadList(n);
	    }
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
	    Log.d(LOG_TAG,
		    "SpinnerCollections_OnItemSelected#onNothingSelected Start");
	    deleteList(null);
	}
    }

    /**
     * ダイアログを閉じるだけの単純な{@link DialogInterface.OnClickListener}
     */
    private final class Dialog_Dismiss implements
	    DialogInterface.OnClickListener {
	@Override
	public void onClick(DialogInterface dialog, int which) {
	    dialog.dismiss();
	}
    }

}