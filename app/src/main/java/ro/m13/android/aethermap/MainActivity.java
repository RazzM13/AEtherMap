package ro.m13.android.aethermap;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.EditText;

import com.google.common.collect.EvictingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MainActivity extends AppCompatActivity {

    class WebViewHandler {

        private ConcurrentMap<Integer, Integer[]> markerData = new ConcurrentHashMap<>();
        private ConcurrentMap<Integer, String[]> markerPrefs = new ConcurrentHashMap<>();

        public void addMarker(Integer id) {
            int colorIdx = Math.floorMod(id, 63);
            int colorR = 0;
            int colorG = 0;
            int colorB = 0;
            while (colorIdx > 0) {
                colorR = 0;
                while (colorR < 255 && colorIdx > 0) {
                    colorB = 0;
                    while (colorB < 255 && colorIdx > 0) {
                        colorG = 0;
                        while (colorG < 255  && colorIdx > 0) {
                            colorG += 63;
                            colorIdx--;
                        }
                        colorB += 63;
                    }
                    colorR += 63;
                }
            }

            String R = Integer.toString(colorR);
            String G = Integer.toString(colorG);
            String B = Integer.toString(colorB);
            String color = String.join(", ", R, G, B);
            markerData.put(id, new Integer[] {0,0,0,0});
            markerPrefs.put(id, new String[] {color});
        }

        public void removeMarker(Integer id) {
            markerData.remove(id);
            markerPrefs.remove(id);
        }

        public void setMarkerData(Integer id, Integer[] data) {
            if (markerData.containsKey(id)) {
                markerData.put(id, data);
            }
        }

        public void setMarkerPrefs(Integer id, String[] prefs) {
            if (markerPrefs.containsKey(id)) {
                markerPrefs.put(id, prefs);
            }
        }

        @JavascriptInterface
        public void log(String msg) {
            Log.d(Utils.AppID(), this.getClass().getName() + " - " + msg);
        }

        @JavascriptInterface
        public String getData() {
            JSONArray labels = new JSONArray();
            labels.put("T15");
            labels.put("T10");
            labels.put("T5");
            labels.put("NOW");

            // build the datasets
            JSONArray datasets = new JSONArray();

            for (Integer marker: markerData.keySet()) {
                Integer[] data = markerData.get(marker);
                String[] prefs = markerPrefs.get(marker);

                // early exit for missing marker data or prefs
                if ( (data == null) || (prefs == null) ) { continue; }

                JSONArray itemData = new JSONArray();
                itemData.put(data[0]);
                itemData.put(data[1]);
                itemData.put(data[2]);
                itemData.put(data[3]);

                String color = prefs[0];

                JSONObject item = new JSONObject();
                try {
                    item.put("label", Integer.toString(marker));
                    item.put("data", itemData);
                    item.put("backgroundColor", "rgba(" + color + ", 0.2)");
                    item.put("borderColor", "rgba(" + color + ", 1)");
                    item.put("borderWidth", "1");
                } catch (JSONException e) {}

                datasets.put(item);
            }

            JSONObject result = new JSONObject();
            try {
                result.put("labels", labels);
                result.put("datasets", datasets);
            } catch (JSONException e) {}

            return result.toString();
        }

    }

    class UIChartThread extends Thread {

        private SensorManager mSensorManager;
        private AEtherSampler mAEtherSampler;
        private WebViewHandler mWebViewHandler;

        Set<String> mMarkers = new HashSet<>();
        Queue<AEtherSample> mDataset;
        long mSamplingInterval;

        public UIChartThread(SensorManager pSensorManager, Sensor[] sources, long samplingInterval) {
            super();

            mSensorManager = pSensorManager;
            mAEtherSampler = new AEtherSampler(pSensorManager, sources, samplingInterval);
            mWebViewHandler = new WebViewHandler();
            mSamplingInterval = samplingInterval;

            // the dataset should be able to satisfy T15 aggregation capacity requirements
            int datasetSize = Math.round(15 * 60 * (mSamplingInterval / 1000));
            mDataset = EvictingQueue.create(datasetSize);

            WebView webView = (WebView) findViewById(R.id.webview);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.addJavascriptInterface(mWebViewHandler, "WebViewHandler");
            webView.loadUrl("file:///android_asset/webview/index.html");
        }

        public void addMarker(String id) {
            int idInt = Integer.parseInt(id);
            mMarkers.add(id);
            mAEtherSampler.addMarker(idInt);
            mWebViewHandler.addMarker(idInt);
        }

        public void removeMarker(String id) {
            int idInt = Integer.parseInt(id);
            mMarkers.remove(id);
            mAEtherSampler.removeMarker(idInt);
            mWebViewHandler.removeMarker(idInt);
        }

        @Override
        public void run() {
            mAEtherSampler.start();

            Log.d(Utils.AppID(), this.getClass().getName() + " - Started");

            while ( !this.isInterrupted() ) {
                AEtherSample latestSample;

                // acquire sample
                while ( (latestSample = mAEtherSampler.read()) == null) {
                    try {
                        Thread.sleep(mSamplingInterval);
                    } catch (InterruptedException e) { this.interrupt(); break; }
                }

                // skip invalid sample
                if (latestSample == null) { continue; }

                mDataset.add(latestSample);

                String[] markersAsStrings = mMarkers.toArray(new String[0]);
                Integer[] markersAsInts = new Integer[markersAsStrings.length];
                for (int x = 0; x < markersAsStrings.length; x++) {
                    markersAsInts[x] = Integer.parseInt(markersAsStrings[x]);
                }

                long T0 = System.currentTimeMillis();
                long T5end = T0;
                long T5start = T5end - (5 * 60 * 1000);
                long T10end = T0;
                long T10start = T10end - (10 * 60 * 1000);
                long T15end = T0;
                long T15start = T15end - (15 * 60 * 1000);


                Map<Integer, Integer> T0MarkerCountMap = latestSample.data;
                Map<Integer, Integer> T5MarkerAverageCountMap = averageSamples(mDataset.iterator(), markersAsInts, T5start, T5end);
                Map<Integer, Integer> T10MarkerAverageCountMap = averageSamples(mDataset.iterator(), markersAsInts, T10start, T10end);
                Map<Integer, Integer> T15MarkerAverageCountMap = averageSamples(mDataset.iterator(), markersAsInts, T15start, T15end);

                for (Integer marker: markersAsInts) {
                    Integer markerT0Data = T0MarkerCountMap.get(marker);
                    Integer markerT5Data = T5MarkerAverageCountMap.get(marker);
                    Integer markerT10Data = T10MarkerAverageCountMap.get(marker);
                    Integer markerT15Data = T15MarkerAverageCountMap.get(marker);
                    Integer[] markerData = new Integer[] {markerT15Data, markerT10Data, markerT5Data, markerT0Data};
                    mWebViewHandler.setMarkerData(marker, markerData);
                }
            }

            Log.d(Utils.AppID(), this.getClass().getName() + " - Interrupted");

            mAEtherSampler.stop();
        }

        private Map<Integer, Integer> averageSamples(Iterator<AEtherSample> sampleIterator, Integer[] markers,
                                                     long startTimestamp, long endTimestamp) {
            Map<Integer, Integer> markerAverageCountMap = new HashMap<>();

            int x = 0;
            while ( sampleIterator.hasNext() && (x < endTimestamp) ) {
                AEtherSample sample = sampleIterator.next();

                // skip samples that are not within our scope
                if ( (sample.timestamp < startTimestamp) || (sample.timestamp >= endTimestamp) ) {
                    continue;
                }

                // accumulate marker counts
                for (int marker: markers) {
                    int markerCount = sample.data.getOrDefault(marker, 0);
                    int markerAverageCount = markerAverageCountMap.getOrDefault(marker, 0);
                    markerAverageCount = markerAverageCount + markerCount;
                    markerAverageCountMap.put(marker, markerAverageCount);
                }

                x++;
            }

            // average the marker counts
            for (int marker: markers) {
                int markerAverageCount = markerAverageCountMap.get(marker);
                markerAverageCount = Math.floorDiv(markerAverageCount, x);
                markerAverageCountMap.put(marker, markerAverageCount);
            }

            return markerAverageCountMap;
        }

    }

    private UIChartThread uiChartThread;

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Log.d(Utils.AppID(), this.getClass().getName() + " - Saving instance state");

        savedInstanceState.putSerializable("markers", (Serializable) uiChartThread.mMarkers);
        savedInstanceState.putSerializable("dataset", (Serializable) uiChartThread.mDataset);
        savedInstanceState.putLong("samplingInterval", uiChartThread.mSamplingInterval);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(Utils.AppID(), this.getClass().getName() + " - Creating activity");

        setContentView(R.layout.activity_main);
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor[] sensors = new Sensor[] { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED) };
        uiChartThread = new UIChartThread(sensorManager, sensors, 1000L);

        // restore saved state
        if (savedInstanceState != null) {
            Log.d(Utils.AppID(), this.getClass().getName() + " - Restoring saved state");

            for (String marker: (Set<String>) savedInstanceState.getSerializable("markers")) {
                uiChartThread.addMarker(marker);
            }
            uiChartThread.mDataset = (Queue<AEtherSample>) savedInstanceState.getSerializable("dataset");
            uiChartThread.mSamplingInterval = savedInstanceState.getLong("samplingInterval");

            // remove previous state
            savedInstanceState.remove("markers");
            savedInstanceState.remove("dataset");
            savedInstanceState.remove("samplingInterval");

        // restore user preferences
        } else {
            Log.d(Utils.AppID(), this.getClass().getName() + " - Restoring user preferences");

            SharedPreferences prefs = getSharedPreferences(Utils.AppID(), MODE_PRIVATE);
            for (String marker: prefs.getStringSet("markers", new HashSet<String>())) {
                uiChartThread.addMarker(marker);
            }
        }

        uiChartThread.start();
    }

    private void updateMarkerPreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(Utils.AppID(), MODE_PRIVATE).edit();
        editor.putStringSet("markers", uiChartThread.mMarkers);
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        Log.d(Utils.AppID(), this.getClass().getName() + " - Destroying instance");

        uiChartThread.interrupt();
        updateMarkerPreferences();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_add_marker) {
            showAddMarkerDialog();
            return true;
        }

        if (id == R.id.action_remove_marker) {
            showRemoveMarkerDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showAddMarkerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add marker");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    uiChartThread.addMarker(input.getText().toString());
                } catch (NumberFormatException e) {}
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void showRemoveMarkerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Remove marker");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    uiChartThread.removeMarker(input.getText().toString());
                } catch (NumberFormatException e) {}
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

}
