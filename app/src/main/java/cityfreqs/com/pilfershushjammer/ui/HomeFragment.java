package cityfreqs.com.pilfershushjammer.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import cityfreqs.com.pilfershushjammer.R;
import cityfreqs.com.pilfershushjammer.jammers.ActiveJammerService;
import cityfreqs.com.pilfershushjammer.jammers.PassiveJammerService;
import cityfreqs.com.pilfershushjammer.utilities.AudioChecker;
import cityfreqs.com.pilfershushjammer.utilities.AudioSettings;
import cityfreqs.com.pilfershushjammer.utilities.BackgroundChecker;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.AUDIO_SERVICE;

public class HomeFragment extends Fragment {
    private static final String TAG = "PilferShush_Jammer-HOME";

    private SharedPreferences sharedPrefs;
    private SharedPreferences.Editor sharedPrefsEditor;

    private boolean PASSIVE_RUNNING;
    private boolean ACTIVE_RUNNING;
    private boolean IRQ_TELEPHONY;
    private boolean DEBUG;

    private TextView homeText;

    private Context context;
    private Bundle audioBundle;
    private AudioChecker audioChecker;

    private ToggleButton passiveJammerButton;
    private ToggleButton activeJammerButton;

    private AudioManager audioManager;
    private BackgroundChecker backgroundChecker;

    HomeFragment(Bundle audioBundle) {
        this.audioBundle = audioBundle;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
        audioManager = (AudioManager)context.getSystemService(AUDIO_SERVICE);
        audioChecker = new AudioChecker(context, audioBundle);
        DEBUG = audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15], false);
        Log.d(TAG, "ON-ATTACH");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        homeText = view.findViewById(R.id.home_text);
        passiveJammerButton = view.findViewById(R.id.run_passive_button);
        activeJammerButton = view.findViewById(R.id.run_active_button);

        populateElements();
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // called when fragment's activity method has returned
        Log.d(TAG, "OnActivityCreated called.");
        initApplication();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (getView() != null) {
            Log.d(TAG, "Home view visible");
        }
        else {
            Log.d(TAG, "Home view null");
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        PASSIVE_RUNNING = sharedPrefs.getBoolean("passive_running", false);
        ACTIVE_RUNNING = sharedPrefs.getBoolean("active_running", false);
        IRQ_TELEPHONY = sharedPrefs.getBoolean("irq_telephony", false);
        //DEBUG = sharedPrefs.getBoolean("debug", false);

        // override check for return from system destroy
        if (checkServiceRunning(PassiveJammerService.class)) {
            // jammer is running
            if (DEBUG) entryLogger(getResources().getString(R.string.resume_status_1), false);
        }
        else {
            if (DEBUG) entryLogger(getResources().getString(R.string.resume_status_2), false);
            PASSIVE_RUNNING = false;
        }
        if (checkServiceRunning(ActiveJammerService.class)) {
            // jammer is running
            if (DEBUG) entryLogger(getResources().getString(R.string.resume_status_3), false);
        }
        else {
            if (DEBUG) entryLogger(getResources().getString(R.string.resume_status_4), false);
            ACTIVE_RUNNING = false;
        }

        int status = audioFocusCheck();

        if (IRQ_TELEPHONY && PASSIVE_RUNNING) {
            // return from background with state irq_telephony and passive_running
            // check audio focus status
            if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

                if (DEBUG) entryLogger(getResources().getString(R.string.resume_status_5), false);
                // reset booleans to init state
                PASSIVE_RUNNING = false;
                IRQ_TELEPHONY = false;
                // cancel notification as part of reset
                //runPassive();
            }
            // we could be checking against ourselves...
            else if (status == AudioManager.AUDIOFOCUS_LOSS) {
                // possible music player etc that has speaker focus but no need of microphone,
                if (DEBUG) entryLogger(getResources().getString(R.string.resume_status_6), false);
            }
        }
        else if (PASSIVE_RUNNING) {
            // return from background without irq_telephony
            entryLogger(getResources().getString(R.string.app_status_1), true);
            // check button state on
            if (!passiveJammerButton.isChecked()) {
                passiveJammerButton.toggle();
            }
        }
        else {
            entryLogger(getResources().getString(R.string.app_status_2), false);
            // check button state off
            if (passiveJammerButton.isChecked()) {
                passiveJammerButton.toggle();
            }
        }
        if (ACTIVE_RUNNING) {
            // return from background without irq_telephony
            entryLogger(getResources().getString(R.string.app_status_3), true);
            // check button state on
            if (!activeJammerButton.isChecked()) {
                activeJammerButton.toggle();
            }
        }
        else {
            entryLogger(getResources().getString(R.string.app_status_4), false);
            // check button state off
            if (activeJammerButton.isChecked()) {
                activeJammerButton.toggle();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // save state first
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPrefsEditor = sharedPrefs.edit();
        sharedPrefsEditor.putBoolean("passive_running", PASSIVE_RUNNING);
        sharedPrefsEditor.putBoolean("active_running", ACTIVE_RUNNING);
        sharedPrefsEditor.putBoolean("irq_telephony", IRQ_TELEPHONY);
        //sharedPrefsEditor.putBoolean("debug", DEBUG);
        sharedPrefsEditor.apply();
        // check toggle jammer off (UI) due to irq_telephony
        if (PASSIVE_RUNNING && IRQ_TELEPHONY) {
            stopPassive();
        }
        if (ACTIVE_RUNNING && IRQ_TELEPHONY) {
            stopActive();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "ONSTOP CALLED.");
        // save state
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPrefsEditor = sharedPrefs.edit();
        sharedPrefsEditor.putBoolean("passive_running", PASSIVE_RUNNING);
        sharedPrefsEditor.putBoolean("active_running", ACTIVE_RUNNING);
        sharedPrefsEditor.putBoolean("irq_telephony", IRQ_TELEPHONY);
        //sharedPrefsEditor.putBoolean("debug", DEBUG);
        sharedPrefsEditor.apply();
    }

    /**********************************************************************************************/

    private void populateElements(){
        homeText.setTextColor(Color.parseColor("#00ff00"));
        homeText.setMovementMethod(new ScrollingMovementMethod());
        homeText.setSoundEffectsEnabled(false); // no further click sounds
        homeText.setOnClickListener(new TextView.OnClickListener() {
            @Override
            public void onClick(View v) {
                // nothing
            }
        });

        passiveJammerButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (DEBUG) entryLogger("PASSIVE IS CHECKED", false);
                    runPassive();
                }
                else {
                    stopPassive();
                }
            }
        });

        activeJammerButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    runActive();
                }
                else {
                    stopActive();
                }
            }
        });
        displayIntroText();
    }

    private void displayIntroText() {
        // simple and understandable statements about app usage.
        entryLogger(getResources().getString(R.string.intro_1) + "\n", false);
        entryLogger(getResources().getString(R.string.intro_2) + "\n", false);
        entryLogger(getResources().getString(R.string.intro_3) + "\n", false);
        entryLogger(getResources().getString(R.string.intro_4) + "\n", true);
    }

    /**********************************************************************************************/


    private void runPassive() {
        if (PASSIVE_RUNNING) {
            if (DEBUG) entryLogger(getResources().getString(R.string.resume_status_7), false);
        }
        else {
            Intent startIntent = new Intent(getActivity(), PassiveJammerService.class);
            startIntent.setAction(PassiveJammerService.ACTION_START_PASSIVE);
            startIntent.putExtras(audioBundle);
            context.startService(startIntent);
            PASSIVE_RUNNING = true;
            entryLogger(getResources().getString(R.string.main_scanner_3), true);
        }
    }

    private void stopPassive() {
        Intent stopIntent = new Intent(getActivity(), PassiveJammerService.class);
        stopIntent.setAction(PassiveJammerService.ACTION_STOP_PASSIVE);
        context.startService(stopIntent);
        PASSIVE_RUNNING = false;
        entryLogger(getResources().getString(R.string.main_scanner_4), true);
    }

    private void runActive() {
        if (ACTIVE_RUNNING) {
            if (DEBUG) entryLogger(getResources().getString(R.string.resume_status_8), false);
        }
        else {
            Intent startIntent = new Intent(getActivity(), ActiveJammerService.class);
            startIntent.setAction(ActiveJammerService.ACTION_START_ACTIVE);
            startIntent.putExtras(audioBundle);
            context.startService(startIntent);
            ACTIVE_RUNNING = true;
            entryLogger(getResources().getString(R.string.main_scanner_5), true);
        }
    }

    private void stopActive() {
        Intent stopIntent = new Intent(getActivity(), ActiveJammerService.class);
        stopIntent.setAction(ActiveJammerService.ACTION_STOP_ACTIVE);
        context.startService(stopIntent);
        ACTIVE_RUNNING = false;
        entryLogger(getResources().getString(R.string.main_scanner_6), true);
    }

    /**********************************************************************************************/

    private void initApplication() {
        Log.d(TAG, "INIT_APPLICATION");
        // apply audio checker settings to bundle for services
        audioBundle.putBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[7], true);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[8], AudioSettings.JAMMER_TYPE_TEST);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[9], AudioSettings.CARRIER_TEST_FREQUENCY);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[10], AudioSettings.DEFAULT_RANGE_DRIFT_LIMIT);
        audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[11], AudioSettings.MINIMUM_DRIFT_LIMIT);
        audioBundle.putBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[14], false);

        //audioBundle.putBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15], MainActivity.DEBUG);

        entryLogger(getResources().getString(R.string.audio_check_pre_1), false);
        if (!audioChecker.determineRecordAudioType()) {
            // have a setup error getting the audio for record
            entryLogger(getResources().getString(R.string.audio_check_1), true);
            return;
        }
        entryLogger(getResources().getString(R.string.audio_check_pre_2), false);
        if (!audioChecker.determineOutputAudioType()) {
            // have a setup error getting the audio for output
            entryLogger(getResources().getString(R.string.audio_check_2), true);
            return;
        }
        // background checker
        backgroundChecker = new BackgroundChecker(context, audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15]));

        entryLogger("\n" + getResources().getString(R.string.intro_8) +
                AudioSettings.JAMMER_TYPES[AudioSettings.JAMMER_TYPE_TEST]
                + "\n", false);

        debugLogger("Debug mode set: " + audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15]), true);
        initBackgroundChecker();
    }

    private void initBackgroundChecker() {
        if (backgroundChecker != null) {
            if (runBackgroundChecks()) {
                // report
                int audioNum = backgroundChecker.getUserRecordNumApps();
                if (audioNum > 0) {
                    entryLogger(getResources().getString(R.string.userapp_scan_8) + audioNum, true);
                } else {
                    entryLogger(getResources().getString(R.string.userapp_scan_9), false);
                }
                if (backgroundChecker.hasAudioBeaconApps()) {
                    entryLogger(backgroundChecker.getAudioBeaconAppNames().length
                            + getResources().getString(R.string.userapp_scan_10) + "\n", true);
                } else {
                    entryLogger(getResources().getString(R.string.userapp_scan_11) + "\n", false);
                }
            }
        }
        else {
            if (DEBUG) Log.d(TAG, "backgroundChecker is NULL at init.");
            entryLogger("Background Checker not initialised.", true);
        }
    }

    private boolean runBackgroundChecks() {
        // already checked for null at initBackground
        try {
            if (backgroundChecker.initChecker(context.getPackageManager())) {
                backgroundChecker.runChecker();
                backgroundChecker.checkAudioBeaconApps();
                return true;
            }
            else {
                entryLogger(getResources().getString(R.string.userapp_scan_1), true);
                return false;
            }
        }
        catch(NullPointerException ex) {
            ex.printStackTrace();
            entryLogger(getResources().getString(R.string.userapp_scan_14), true);
        }
        return false;
    }

    private int audioFocusCheck() {
        if (DEBUG) entryLogger(getResources().getString(R.string.audiofocus_check_1), false);
        // note: api 26+ use AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)

        // NOTE: no resource context available in onAudioFocusChange
        final AudioManager.OnAudioFocusChangeListener audioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch(focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        // -1
                        // loss for unknown duration
                        debugLogger("Audio Focus Listener: LOSS.", true);
                        //audioManager.abandonAudioFocus(audioFocusListener);
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // -2
                        // temporary loss ? API docs says a "transient loss"!
                        debugLogger("Audio Focus Listener: LOSS_TRANSIENT.", false);
                        // system forced loss, assuming telephony
                        debugLogger(getResources().getString(R.string.audiofocus_check_8), false);
                        IRQ_TELEPHONY = true;
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        // -3
                        // loss to other audio source, this can duck for the short duration if it wants
                        // can be system notification or ringtone sounds
                        debugLogger("Audio Focus Listener: LOSS_TRANSIENT_DUCK.", false);
                        break;
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        // 0
                        // failed focus change request
                        debugLogger("Audio Focus Listener: REQUEST_FAIL.", true);
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        //case AudioManager.AUDIOFOCUS_REQUEST_GRANTED: <- duplicate int value...
                        // 1
                        // has gain, or request for gain, of unknown duration
                        debugLogger("Audio Focus Listener: GAIN.", false);
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        // 2
                        // temporary gain or request for gain, for short duration (ie. notification)
                        debugLogger("Audio Focus Listener: GAIN_TRANSIENT.", false);
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        // 3
                        // as above but with other background audio ducked for duration
                        debugLogger("Audio Focus Listener: GAIN_TRANSIENT_DUCK.", false);
                        break;
                    default:
                        //
                        debugLogger("Audio Focus Listener: UNKNOWN STATE.", false);
                }
            }
        };
        int result = audioManager.requestAudioFocus(audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (DEBUG) entryLogger(getResources().getString(R.string.audiofocus_check_2), false);
        }
        else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            if (DEBUG) entryLogger(getResources().getString(R.string.audiofocus_check_3), false);
        }
        else {
            if (DEBUG) entryLogger(getResources().getString(R.string.audiofocus_check_4), false);
        }
        return result;
    }

    // As of Build.VERSION_CODES.O, this method is no longer available to third party applications.
    // For backwards compatibility, it will still return the caller's own services.
    private boolean checkServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**********************************************************************************************/

    private void entryLogger(String entry, boolean caution) {
        int start = homeText.getText().length();
        homeText.append("\n" + entry);
        int end = homeText.getText().length();
        Spannable spannableText = (Spannable) homeText.getText();
        if (caution) {
            spannableText.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorAccent)), start, end, 0);
        }
        else {
            spannableText.setSpan(new ForegroundColorSpan(Color.WHITE), start, end, 0);
        }
    }

    private void debugLogger(String message, boolean caution) {
        // for the times that fragments arent attached etc, print to adb
        if (caution && DEBUG) {
            Log.e(TAG, message);
        }
        else if ((!caution) && DEBUG) {
            Log.d(TAG, message);
        }
        else {
            Log.i(TAG, message);
        }
    }
}
