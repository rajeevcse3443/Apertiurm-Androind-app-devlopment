/**
 * ApertiumActivity.java
 * Main Launcher Activity of application
 * @author Arink Verma
 * 
 */


package org.apertium.android;

import java.lang.Thread.UncaughtExceptionHandler;

import org.apertium.Translator;
import org.apertium.android.DB.DatabaseHandler;
import org.apertium.android.filemanager.FileManager;
import org.apertium.android.helper.AppPreference;
import org.apertium.android.helper.ClipboardHandler;
import org.apertium.android.languagepair.RulesHandler;
import org.apertium.android.languagepair.TranslationMode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class ApertiumActivity extends Activity implements OnClickListener{
    private final String TAG = "ApertiumActiviy";
    private Activity thisActivity = null;
    
    /*Layout variable*/
    //Text Fields
    private EditText inputEditText;
    private TextView outputTextView;
    //Button
    private Button submitButton;
    private Button toButton;
    private Button fromButton;
    private Button dirButton;
    
    /*Mode related variable*/
    private String currentMode = null;
    private String fromLanguage = null;
    private String toLanguage = null;
    private String outputText = null;
    private String inputText = null;
    private TranslationMode translationMode = null;
    
    /*Data Handler
     * Data which persist */
    private AppPreference appPreference = null;
    private ClipboardHandler clipboardHandler = null;
    private DatabaseHandler databaseHandler = null;
    private RulesHandler rulesHandler = null;

    /*Process Handler*/
    /* Lint warning
     * This Handler class should be static or leaks might occur 
     * Android Lint Problem
     */
    private static Handler handler = null;
    private ProgressDialog progressDialog;


    /* OnCreate */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thisActivity = this;
        getExtrasData();
        
        
        appPreference       = new AppPreference(thisActivity);
        databaseHandler     = new DatabaseHandler(thisActivity);
        rulesHandler        = new RulesHandler(thisActivity);
        clipboardHandler    = new ClipboardHandler(thisActivity);
        handler             = new Handler();
        
        /* Recovery and restore states */
        CrashRecovery();
        FileManager.setDIR();
        updateDirChanges();
    
        /* Fetching if mode is sent by widgets */
        if(currentMode==null){
            currentMode = rulesHandler.getCurrentMode();
        }else{
        	rulesHandler.setCurrentMode(currentMode);
        }

        
        /* Setting up Translator base and properties */
        Log.i(TAG,"Current mode = "+currentMode+", Cache = "+appPreference.isCacheEnabled()+", Clipboard push get = "+appPreference.isClipBoardPushEnabled()+appPreference.isClipBoardGetEnabled());
        translationMode = databaseHandler.getMode(currentMode);
        if(translationMode!=null && translationMode.isValid()){
            try {
                Log.i(TAG,"ExtractPath ="+rulesHandler.ExtractPathCurrentPackage()+", Jar= "+rulesHandler.PathCurrentPackage());
                Translator.setBase(rulesHandler.ExtractPathCurrentPackage(), rulesHandler.getClassLoader());
                Translator.setDelayedNodeLoadingEnabled(true);
                Translator.setMemmappingEnabled(true);
                Translator.setParallelProcessingEnabled(false);
                Translator.setCacheEnabled(appPreference.isCacheEnabled());
            } catch (Exception e) {
                Log.e(TAG, "Error while loading class"+e);
                e.printStackTrace(); 
            }
        }else{
            rulesHandler.clearCurrentMode();
        }

        /* Generating layout view */
        initView();
    }
    
    /* OnResume */
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG,"onResume mode=" + rulesHandler.getCurrentMode());
        
        /* Fetching if mode is sent by widgets */
        getExtrasData();

        /**Giving priority to incoming text from intent over clipboardtext*/
        if(inputText==null){
        	if(appPreference.isClipBoardGetEnabled()){
        		inputText = clipboardHandler.getText();
            }
        }
        
        if(inputText!=null){
            inputEditText.setText(inputText);
        }
        
        if(currentMode==null){
            currentMode = rulesHandler.getCurrentMode();
        }else{
        	rulesHandler.setCurrentMode(currentMode);
        }
        
        /* updating if mode is changed */
        translationMode = databaseHandler.getMode(currentMode);
        if(translationMode!= null && translationMode.isValid()){
            UpdateMode();
        }else{
            Log.i(TAG,"Invalid mode");
            toButton.setText(R.string.to);
            fromButton.setText(R.string.from);
            
            toLanguage  = getString(R.string.to);
            fromLanguage    = getString(R.string.from);
        }
    }

    void getExtrasData(){
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String BundleMODE = extras.getString("Mode");
            if(BundleMODE!=null){
                currentMode = BundleMODE;
            }
             BundleMODE = extras.getString("input");
            if(BundleMODE!=null){
            	inputText = BundleMODE;
            }
        }
    }
    
    /* Init View,
     * Initialing view */
    private void initView() {
        Log.i(TAG,"ApertiumActivityInitView Started");
        setContentView(R.layout.main_layout);
        inputEditText       = (EditText) findViewById(R.id.inputtext);
        /**Giving priority to incoming text from intent over clipboardtext*/
        if(inputText==null){
        	if(appPreference.isClipBoardGetEnabled()){
        		inputText = clipboardHandler.getText();
            }
        }
        
        if(inputText!=null){
            inputEditText.setText(inputText);
        }

        submitButton    = (Button) findViewById(R.id.translateButton);
        outputTextView  = (TextView) findViewById(R.id.outputText);
        toButton        = (Button) findViewById(R.id.toButton);
        fromButton      = (Button) findViewById(R.id.fromButton);
        dirButton       = (Button) findViewById(R.id.modeSwitch);

        submitButton.setOnClickListener(this);
        toButton.setOnClickListener(this);
        fromButton.setOnClickListener(this);
        dirButton.setOnClickListener(this);
    }

    
    /* Update Translator mode if user change */
    private void UpdateMode(){
        if (currentMode==null) {
            if(databaseHandler.getAllModes().isEmpty()){
                // No modes, go to download
                startActivity(new Intent(ApertiumActivity.this, DownloadActivity.class));
            }
            toButton.setText(R.string.to);
            toButton.setText(R.string.from);
            return;
        }
        

        Log.i(TAG,"UpdateMode ="+currentMode+", cache= "+appPreference.isCacheEnabled());

        try {

            String currentPackage   = rulesHandler.getCurrentPackage();
            String PackageTOLoad    = rulesHandler.findPackage(currentMode);
            
            //If mode is changed
            if(!currentMode.equals(rulesHandler.getCurrentMode())){
                Log.i(TAG,"Mode changed , setCurrentMode="+currentMode);               
                rulesHandler.setCurrentMode(currentMode);
            }
            
            //If package is changed
            if(currentPackage==null || !currentPackage.equals(PackageTOLoad)){
                Log.i(TAG,"BaseChanged ="+rulesHandler.getClassLoader()+"path = "+rulesHandler.ExtractPathCurrentPackage());
                Translator.setBase(rulesHandler.ExtractPathCurrentPackage(), rulesHandler.getClassLoader());
                Translator.setDelayedNodeLoadingEnabled(true);
                Translator.setMemmappingEnabled(true);
                Translator.setParallelProcessingEnabled(false);
                Translator.setCacheEnabled(appPreference.isCacheEnabled());
            }
                        
            Translator.setMode(currentMode);

            translationMode = databaseHandler.getMode(currentMode);
            fromLanguage    = translationMode.getFrom();
            toLanguage      = translationMode.getTo();
            
            toButton.setText(toLanguage);
            fromButton.setText(fromLanguage);
        } catch (Exception e) {
            Log.e(TAG,"UpdateMode "+e+"Mode = "+currentMode);
            e.printStackTrace();
        }

    }
    
    
    @Override
    public void onClick(View v) {
        
        if (v.equals(submitButton)){
            if(translationMode!= null && translationMode.isValid()){
                //Hiding soft keypad
                InputMethodManager inputManager = (InputMethodManager)this.getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(inputEditText.getApplicationWindowToken(), 0);
        
                TranslationRun();
            }
        }else if(v.equals(fromButton)){
            
            if (databaseHandler.getAllModes().isEmpty()) {                  
                // No modes, go to download
                startActivity(new Intent(ApertiumActivity.this, DownloadActivity.class));
                return;
            } 
                
            final String[] ModeTitle = databaseHandler.getModeTitlesOut();          

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.translate_from));
            builder.setItems(ModeTitle, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int position) {
                    Toast.makeText(getApplicationContext(), ModeTitle[position],   Toast.LENGTH_SHORT).show();
                    fromLanguage = ModeTitle[position];
                    toLanguage = null;
                    fromButton.setText(fromLanguage);
                    toButton.setText(R.string.to);                  
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }else if(v.equals(toButton)){
            
            if (databaseHandler.getAllModes().isEmpty()) {                  
                // No modes, go to download
                startActivity(new Intent(ApertiumActivity.this, DownloadActivity.class));
                return;
            } 

            final String[] ModeTitle =  databaseHandler.getModeTitlesInFrom(fromLanguage);
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.translate_to));
            builder.setItems(ModeTitle, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int position) {
                    Toast.makeText(getApplicationContext(), ModeTitle[position],   Toast.LENGTH_SHORT).show();
                    toLanguage = ModeTitle[position];
                    toButton.setText(toLanguage);
                    Log.i(TAG,databaseHandler.getModeID(fromLanguage,toLanguage));
                    currentMode = databaseHandler.getModeID(fromLanguage,toLanguage);
                    
                    UpdateMode();
                }
            });
            
            AlertDialog alert = builder.create();
            alert.show();
        }else if(v.equals(dirButton)){
            String temp = fromLanguage;
            fromLanguage = toLanguage;
            toLanguage = temp;
            temp = databaseHandler.getModeID(fromLanguage,toLanguage);
            if(temp == null){
                Toast.makeText(getApplicationContext(), getString(R.string.no_mode_available,fromLanguage,toLanguage),   Toast.LENGTH_SHORT).show();
                temp = fromLanguage;
                fromLanguage = toLanguage;
                toLanguage = temp;                
            }else{
                currentMode = temp;
                UpdateMode();
            }
        }

    }


    /* Translation Thread,
     * Load translation rules and excute lttoolbox.jar */
    private void TranslationRun(){
        progressDialog = ProgressDialog.show(this, getString(R.string.translator), getString(R.string.working),  true,true);
        Thread t = new Thread() {
            @Override
            public void run() {
                String inputText = inputEditText.getText().toString();
                if (!TextUtils.isEmpty(inputText)) {
                    outputText = "";
                    
                    try {
                        Translator.setCacheEnabled(appPreference.isCacheEnabled());
                        Log.i(TAG,"Translator Run Cache ="+appPreference.isCacheEnabled()+", Mark ="+appPreference.isDisplayMarkEnabled()+ ", MODE = "+currentMode);
                        Translator.setDisplayMarks(appPreference.isDisplayMarkEnabled());
                        outputText  = Translator.translate(inputEditText.getText().toString());
                        
                        if(appPreference.isClipBoardPushEnabled()){
                        clipboardHandler.putText(outputText);
                        }
                
                    } catch (Exception e) {
                        Log.e(TAG,"ApertiumActivity.TranslationRun MODE ="+currentMode+";InputText = "+inputEditText.getText());
                        e.printStackTrace(); 
                    }
                }
                
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        outputTextView.setText(outputText);
                        progressDialog.dismiss();
                    }
                });
            }
        };
        t.start();
        
        
        //Saving and setting crash happen flag
        
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                
                Translator.clearCache();
                String error = "["+e+"]";
                Log.e("Error", error);
                appPreference.ReportCrash(error);
                progressDialog.dismiss();
                e.printStackTrace(); 
                thisActivity.finish();
                android.os.Process.killProcess(android.os.Process.myPid());
                
             }
        });
        
    }





    private void updateDirChanges() {
        if(appPreference.isStateChanged()){
            progressDialog = ProgressDialog.show(thisActivity, getString(R.string.updating_db),getString(R.string.working),true,false);
            
            Thread t = new Thread() {
                 @Override
                 public void run() {
                    databaseHandler.updateDB();
                    
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            appPreference.SaveState();
                        }
                    });
                }
            };
            t.start();
        }      
    }


    @SuppressWarnings("deprecation")
    private void CrashRecovery(){
        final String crash = appPreference.GetCrashReport();
        if(crash != null){
            appPreference.ClearCrashReport();
            Log.i(TAG,"Crash on last run time" + crash);
             
            final AlertDialog alertDialog = new AlertDialog.Builder(thisActivity).create();
            alertDialog.setTitle(R.string.crash_detect);
            alertDialog.setMessage(getString(R.string.crash_message_with_error_and_support_address,crash,"arinkverma@gmail.com"));
            
            alertDialog.setButton(getString(R.string.report), new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int which) {  
                    Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND); 
                    emailIntent.setType("plain/text");
                    String aEmailList[] = { "arinkverma@gmail.com" };     
                    emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, aEmailList);    
                    emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Apertium Android Error Report");   
                    emailIntent.setType("plain/text");  
                    emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Error "+crash);  
                    startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email_in))); 
                    alertDialog.dismiss();         
             } });
            
            alertDialog.setButton2(getString(R.string.setting), new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int which) {
           
                    final Intent myIntent = new Intent(ApertiumActivity.this, ManageActivity.class);
                    ApertiumActivity.this.startActivity(myIntent);
           
             } });
            
            alertDialog.show();
        
        }
    }
    
    
    
    /****
     *  Option menu 
     *  1. share
     *  2. setting
     *  3. inbox*/
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
     }

    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent myIntent = null;
        switch (item.getItemId()) {
            case R.id.manage:
                myIntent = new Intent(ApertiumActivity.this, ManageActivity.class);
                ApertiumActivity.this.startActivity(myIntent);
                return true;
            case R.id.share:
                share_text();
                return true;
            case R.id.inbox:
                myIntent = new Intent(ApertiumActivity.this, SMSInboxActivity.class);
                ApertiumActivity.this.startActivity(myIntent);
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    /* Share text
     * Intent to share translated text over other installed application services */
    private void share_text() {
        Log.i(TAG,"ApertiumActivity.share_text Started");
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Apertium Translate");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, outputText);
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
    }

}