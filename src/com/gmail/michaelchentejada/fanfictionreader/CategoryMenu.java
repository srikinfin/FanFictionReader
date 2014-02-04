/**
 * 
 */
package com.gmail.michaelchentejada.fanfictionreader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.jsoup.Jsoup;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * The class which shows the main categories in the fan fiction site.
 * @author Michael Chen
 */
public class CategoryMenu extends Activity {
	
	private enum classState{
		NORMAL,
		CROSSOVER,
		COMMUNITIES
	}
	
	/*
	 * Variables representing current state
	 */
	protected final static String COMMUNITY = "Community";
	
	private classState appState;
	private boolean sort; //true = a-z ; false = views
	private int position2=0;
	
	private Context context;
	private ListView listView;
	
	private parseSite asynctask = new parseSite();
	private final OnItemClickListener listener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
			Intent i;
			switch (appState) {
			case CROSSOVER:
				i = new Intent(context,SubCategoryMenu.class);
				i.putExtra("URL", list.get((int)id).get(Parser.URL));	
				break;
			case NORMAL:
				i = new Intent(context,StoryMenu.class);
				i.putExtra("URL", list.get((int)id).get(Parser.URL));
				break;
			case COMMUNITIES: default:
				i = new Intent(context,CommunityMenu.class);
				i.putExtra(CommunityMenu.URL, list.get((int)id).get(Parser.URL));
				break;
			}
			startActivity(i);
		}
	};
	private final OnItemSelectedListener filterListener = new OnItemSelectedListener() {

		@Override
		public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id2) {	
			if  (position != position2){
				position2 = position;
				asynctask.cancel(true);
				asynctask =  new parseSite();
				asynctask.execute();
			}
		}

		@Override
		public void onNothingSelected(AdapterView<?> arg0) {		
		}
	};
	private final OnCheckedChangeListener sortListener = new OnCheckedChangeListener() {
		
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			Collections.sort(list, new ListComparator(isChecked));
			SimpleAdapter adapter = new SimpleAdapter(context, list, R.layout.category_menu_list_item, new String[] {Parser.TITLE,Parser.VIEWS}, new int[] {R.id.category_menu_title,R.id.category_menu_views});
			listView.setAdapter(adapter);
			sort = isChecked;
		}
	};
	
	private static final String[] FANFIC_URLS = {
		"anime/",
		"book/",
		"cartoon/",
		"comic/",
		"game/",
		"misc/",
		"movie/",
		"play/",
		"tv/"}; 
	private ArrayList<HashMap<String, String>> list;
	private int id = 0;
	
	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.activity_list_view);
		
		context = this;
		listView = (ListView) findViewById(R.id.menuListView);
		View header = (View)getLayoutInflater().inflate(R.layout.category_menu_header, null);
		listView.addHeaderView(header);
		listView.setOnItemClickListener(listener);
		
		
		Spinner spinner = (Spinner)header.findViewById(R.id.category_menu_header_filter);
		List<String> filterList = new ArrayList<String>();
		filterList.add(getString(R.string.top_200));
		filterList.add("#");
		for (char i = 'A'; i <= 'Z'; i++) {
			filterList.add(Character.toString(i));
		}
		
		position2 = (savedInstanceState == null)? 0 : savedInstanceState.getInt("Positon");
		ArrayAdapter<String> filterAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, filterList);
		filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(filterAdapter);
		spinner.setSelection(position2);
		spinner.setOnItemSelectedListener(filterListener);
		
		if (getIntent().getBooleanExtra("Crossover", false)) {
			appState = classState.CROSSOVER;
		} else if (getIntent().getBooleanExtra(COMMUNITY, false)){
			appState = classState.COMMUNITIES;
		}else {
			appState = classState.NORMAL;
		}

		
		sort = (savedInstanceState==null)? false : savedInstanceState.getBoolean("Sort");
		ToggleButton sortButton = (ToggleButton)findViewById(R.id.category_menu_sort);
		sortButton.setChecked(sort);
		sortButton.setOnCheckedChangeListener(sortListener);	
		
		if (savedInstanceState == null){
			id = getIntent().getIntExtra("Id", 0);	
			if (id >FANFIC_URLS.length){
				Toast toast = Toast.makeText(context, getString(R.string.dialog_unspecified), Toast.LENGTH_SHORT);
				toast.show();
				finish();
			}else{
				asynctask.execute();
			}
			
		}else {
			list = (ArrayList<HashMap<String, String>>) savedInstanceState.getSerializable("List");
			SimpleAdapter adapter = new SimpleAdapter(context, list, R.layout.category_menu_list_item, new String[] {Parser.TITLE,Parser.VIEWS}, new int[] {R.id.category_menu_title,R.id.category_menu_views});
			listView.setAdapter(adapter);	
		}
}
	
	/**
	 * Saves the current list to avoid having to fetch it again upon screen rotation & pause.
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putSerializable("List", list);
		outState.putInt("Positon", position2);
		outState.putBoolean("Sort", sort);
		super.onSaveInstanceState(outState);
	}
		
	/**
	 * The asynchronous class which obtains the list of categories from fanfiction.net. 
	 * @author Michael Chen
	 *
	 */
	private class parseSite extends AsyncTask<Void, Void, ArrayList<HashMap<String, String>>>{
		private ProgressDialog progress;
		
		@Override
		protected void onPreExecute() {
			progress = new ProgressDialog(context);
			progress.setTitle("");
			progress.setMessage(getResources().getString(R.string.dialog_loading));
			progress.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					finish();	
				}
			});
			progress.show();
			super.onPreExecute();
		}
		
		@Override
		protected ArrayList<HashMap<String, String>> doInBackground(Void... params) {
			String url = "";
			switch (appState) {
			case COMMUNITIES:
				url = "https://m.fanfiction.net/communities/" +FANFIC_URLS[id] + "?l=" + sortKey();
				break;
			case CROSSOVER:
				url = "https://m.fanfiction.net/crossovers/" +FANFIC_URLS[id] + "?l=" + sortKey();
				break;
			case NORMAL:
				url = "https://m.fanfiction.net/"+FANFIC_URLS[id] + "?l=" + sortKey(); 
				break;
			}
			
			
			try {
				org.jsoup.nodes.Document document = Jsoup.connect(url).get();
				return Parser.Categories(getString(R.string.Parser_Stories),document);				
			} catch (IOException e) {
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(ArrayList<HashMap<String, String>> result) {
			// To dismiss the dialog
			progress.dismiss();
			if (result != null) {
				list = result;
				Collections.sort(list, new ListComparator(sort));
				SimpleAdapter adapter = new SimpleAdapter(context, list, R.layout.category_menu_list_item, new String[] {Parser.TITLE,Parser.VIEWS}, new int[] {R.id.category_menu_title,R.id.category_menu_views});
				listView.setAdapter(adapter);				
			}else{
				Toast toast = Toast.makeText(context, getString(R.string.dialog_internet), Toast.LENGTH_SHORT);
				toast.show();
				finish();
			}
			super.onPostExecute(result);
		}
		
	}
	
	/**
	 * Stops the asynchronous task if the screen is closed prematurely. 
	 */
	@Override
	protected void onDestroy() {
		asynctask.cancel(true);
		super.onDestroy();
	}
	
	private String sortKey (){
			char selector = ' ';
			if (position2 == 0) {
				selector = ' ';
			}else if (position2 == 1){
				selector = '1';
			}else{
				selector = (char) ('a'+position2-2);
			}
			return Character.toString(selector);
	}
	
}
