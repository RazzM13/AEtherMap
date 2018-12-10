package ro.m13.android.aethermap;

import java.io.Serializable;
import java.util.HashMap;

public class AEtherSample implements Serializable {

    public long timestamp;

    public HashMap<Integer, Integer> data;

    public AEtherSample(long timestamp, HashMap<Integer, Integer> data) {
        this.timestamp = timestamp;
        this.data = data;
    }
}
