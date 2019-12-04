package info.nightscout.androidaps.data;

import java.util.ArrayList;
import java.util.List;

public class MealCarb {

    private List<Long> carbTimes = new ArrayList<>();
    private final long date;

    public MealCarb(long date) {this.date = date;}

    public List<Long> getCarbTimes() {
        return carbTimes;
    }

    public void addCarbTime(Long carbTime) {
        this.carbTimes.add(carbTime);
    }

    public long getDate() {
        return date;
    }

}
