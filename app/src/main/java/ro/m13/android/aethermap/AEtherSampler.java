package ro.m13.android.aethermap;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AEtherSampler implements Runnable, SensorEventListener {

    private Set<Sensor> mSources = new HashSet<>();
    private Set<Integer> mMarkers = new HashSet<>();
    private Queue<AEtherSample> mSamples = new ConcurrentLinkedDeque<>();
    private Queue<Float> mCurrentSampleData = new ConcurrentLinkedDeque<>();
    private Pattern mMarkersPattern;
    private Thread mSamplerThread;
    private SensorManager mSensorManager;
    private long mSamplingInterval;

    public AEtherSampler(SensorManager pSensorManager, Sensor[] pSources, long pSamplingInterval) {
        if (pSensorManager == null) {
            throw new InvalidParameterException("pSensorManager");
        }
        mSensorManager = pSensorManager;

        if (pSamplingInterval == 0) {
            throw new InvalidParameterException("pSamplingInterval");
        }
        mSamplingInterval = pSamplingInterval;


        if (pSources.length == 0) {
            throw new InvalidParameterException("pSources");
        }
        for (Sensor source: pSources) {
            if (source == null) {
                throw new InvalidParameterException("pSources contains invalid source");
            }
            mSources.add(source);
        }

        mSamplerThread = new Thread(this);
    }

    public void addMarker(Integer pMarker) {
        if (pMarker == null) {
            throw new InvalidParameterException();
        }
        mMarkers.add(pMarker);
        updateMarkersPattern();
    }

    public void removeMarker(Integer pMarker) {
        if (pMarker == null) {
            throw new InvalidParameterException();
        }
        mMarkers.remove(pMarker);
        updateMarkersPattern();
    }

    private void updateMarkersPattern() {
        StringBuilder stringBuilder = new StringBuilder();

        for (Integer marker: mMarkers) {
            stringBuilder.append(String.format("(%s)|", marker));
        }

        if (stringBuilder.length() > 0) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            mMarkersPattern = Pattern.compile(stringBuilder.toString());
        } else {
            mMarkersPattern = null;
        }
    }

    public void start() {
        if ( mSamplerThread.isAlive() ) {
            Log.i(Utils.AppID(), this.getClass().getName() + " - Already started!");
            return;
        }

        Log.i(Utils.AppID(), this.getClass().getName() + " - Starting");

        // register input sources
        for (Sensor source : mSources) {
            Log.i(Utils.AppID(), String.format("%s - Registering input source: %s",
                                                this.getClass().getName(), source.getId()));
            mSensorManager.registerListener(this, source, SensorManager.SENSOR_DELAY_FASTEST);
        }

        // start sampling thread
        mSamplerThread.start();
    }

    public void stop() {
        if ( !mSamplerThread.isAlive() ) {
            Log.i(Utils.AppID(), this.getClass().getName() + " - Already stopped!");
            return;
        }

        Log.i(Utils.AppID(), this.getClass().getName() + " - Stopping");

        mSamplerThread.interrupt();

        mSensorManager.unregisterListener(this);

    }

    public AEtherSample read() {
        return mSamples.poll();
    }

    private static AEtherSample data2Sample(Iterator<Float> data, Pattern markerPattern) {

        // convert the data to a string representation of all values
        StringBuilder stringBuilder = new StringBuilder();
        while ( data.hasNext() ) {
            String dataValue = Float.toString(data.next());
            dataValue = dataValue.replaceAll("^.+\\.", ""); // keep decimal portion
            stringBuilder.append(dataValue);
        }
        String dataStr = stringBuilder.toString();

        // pattern match the above string for expected mMarkers
        HashMap<Integer, Integer> markerCountMap = new HashMap<>();
        Matcher mpMatcher = markerPattern.matcher(dataStr);
        while ( mpMatcher.find() ) {
            Integer marker = Integer.parseInt(mpMatcher.group());

            int markerCount = markerCountMap.getOrDefault(marker, 0);
            markerCount++;

            markerCountMap.put(marker, markerCount);
        }

        // build the sample
        long timestamp = System.currentTimeMillis();
        AEtherSample sample = new AEtherSample(timestamp, markerCountMap);

        return sample;
    }

    @Override
    public void run() {
        Log.i(Utils.AppID(), this.getClass().getName() + " - Started");
        try {

            while ( !mSamplerThread.isInterrupted() ) {
                Log.d(Utils.AppID(), String.format("%s - Sleeping for %d",
                                                    this.getClass().getName(), mSamplingInterval));
                Thread.sleep(mSamplingInterval);

                if (mMarkers.size() > 0) {
                    Log.d(Utils.AppID(), this.getClass().getName() + " - Generating a new sample and adding it to the collection");
                    Queue<Float> currentSampleDataClone = new ConcurrentLinkedQueue<>(mCurrentSampleData);
                    AEtherSample sample = data2Sample(currentSampleDataClone.iterator(), mMarkersPattern);
                    mCurrentSampleData.removeAll(currentSampleDataClone);
                    mSamples.add(sample);
                } else {
                    Log.d(Utils.AppID(), this.getClass().getName() + " - Unable to generate sample, no mMarkers defined");
                }
            }

        } catch (InterruptedException e) {
            mSamplerThread.interrupt();
            Log.i(Utils.AppID(), this.getClass().getName() + " - Stopped");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        for (float value: event.values) {
            mCurrentSampleData.add(value);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

}
