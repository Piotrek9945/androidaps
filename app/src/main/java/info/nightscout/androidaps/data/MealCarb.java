package info.nightscout.androidaps.data;

import java.util.List;

public class MealCarb {

    private List<Long> carbTimes;
    private final long mealDate;

    public MealCarb(long mealDate) {this.mealDate = mealDate;}

    public List<Long> getCarbTimes() {
        return carbTimes;
    }

    public void addCarbTime(Long carbTime) {
        this.carbTimes.add(carbTime);
    }

    public long getDate() {
        return mealDate;
    }

}
