package info.nightscout.androidaps.plugins.general.food;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator;
import info.nightscout.androidaps.utils.SafeParse;

import static info.nightscout.androidaps.utils.DateUtil.now;

public class ExtCarbService {

    public ExtCarbService() {}

    public static void generateExtCarbs(Integer extCarb) {
        int duration = getDuration(extCarb);
        Integer extCarbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(extCarb)).value();

        int timeOffset = 0;
        final long time = now() + timeOffset * 1000 * 60;

        if (extCarbsAfterConstraints > 0) {
            CarbsGenerator.generateCarbs(extCarbsAfterConstraints, time, duration, "");
            NSUpload.uploadEvent(CareportalEvent.NOTE, now() - 2000, MainApp.gs(R.string.generated_ecarbs_note, extCarbsAfterConstraints, duration, timeOffset));
        }
    }

    private static int getWBT(int eCarb) {
        return (int) Math.ceil((double) eCarb / 10d);
    }

    private static int getDuration(int extCarb) {
        int wbt = getWBT(extCarb);
        if (wbt > 4) {
            return 8;
        } else {
            return wbt + 2;
        }
    }

    public static int calculateExtCarb(List<Food> foodList) {
        int eCarb = 0;
        for (Food food : foodList) {
            eCarb += calculateExtCarb(food);
        }
        return eCarb;
    }

    static int calculateExtCarb(Food food) {
        final int kcalPerOneCarb = 4;
        final int kcalPerOneFat = 9;
        final int kcalPerOneProtein = 4;

        Double eCarb;
        if (food.energy > 0) {
            eCarb = SafeParse.stringToDouble(
                    String.valueOf(
                            (food.energy - kcalPerOneCarb * food.carbs) / 10
                    )
            );
        } else {
            eCarb = SafeParse.stringToDouble(
                    String.valueOf(
                            (food.fat * kcalPerOneFat + food.protein * kcalPerOneProtein) / 10
                    )
            );
        }

        return (int) Math.floor(eCarb * food.portionCount);
    }



}
