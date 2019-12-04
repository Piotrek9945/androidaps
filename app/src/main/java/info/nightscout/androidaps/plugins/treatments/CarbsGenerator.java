package info.nightscout.androidaps.plugins.treatments;

import android.content.Intent;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.MealCarb;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.T;

import static info.nightscout.androidaps.utils.DateUtil.now;

public class CarbsGenerator {
    public static List<MealCarb> meal = new ArrayList<>();

    public static void generateCarbs(int amount, long startTime, int duration, @Nullable String notes) {
        long remainingCarbs = amount;
        int ticks = (duration * 4); //duration guaranteed to be integer greater zero
        MealCarb mealCarb = new MealCarb(startTime);
        for (int i = 0; i < ticks; i++){
            long carbTime = startTime + i * 15 * 60 * 1000;
            mealCarb.addCarbTime(carbTime);
            int smallCarbAmount = (int) Math.round((1d * remainingCarbs) / (ticks-i));  //on last iteration (ticks-i) is 1 -> smallCarbAmount == remainingCarbs
            remainingCarbs -= smallCarbAmount;
            if (smallCarbAmount > 0)
                createCarb(smallCarbAmount, carbTime, CareportalEvent.MEALBOLUS, notes);
        }
        CarbsGenerator.meal.add(mealCarb);
        CarbsGenerator.removeFinishedMeals();
    }

    private static void removeFinishedMeals() {
        for(MealCarb it : CarbsGenerator.meal) {
            List<Long> carbTimes = it.getCarbTimes();
            Long latestTime = null;
            for (Long carbTime : carbTimes) {
                if (latestTime == null || carbTime > latestTime) {
                    latestTime = carbTime;
                }
            }
            if (latestTime < now()) {
                CarbsGenerator.meal.remove(it);
            }
        }
    }

    public static void createCarb(int carbs, long time, String eventType, @Nullable String notes) {
        DetailedBolusInfo carbInfo = new DetailedBolusInfo();
        carbInfo.date = time;
        carbInfo.eventType = eventType;
        carbInfo.carbs = carbs;
        carbInfo.context = MainApp.instance();
        carbInfo.source = Source.USER;
        carbInfo.notes = notes;
        if (ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().storesCarbInfo && carbInfo.date <= now() && carbInfo.date > now()- T.mins(2).msecs()) {
            ConfigBuilderPlugin.getPlugin().getCommandQueue().bolus(carbInfo, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", result.comment);
                        i.putExtra("title", MainApp.gs(R.string.treatmentdeliveryerror));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainApp.instance().startActivity(i);
                    }
                }
            });
        } else {
            // Don't send to pump if it is in the future or more than 5 minutes in the past
            // as pumps might return those as as "now" when reading the history.
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(carbInfo, false);
        }
    }
}
