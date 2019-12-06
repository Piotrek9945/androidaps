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

public class CarbService {

    public CarbService() {}

    static int calculateCarb(List<Food> foodList) {
        int carbs = 0;
        for (Food food : foodList) {
            carbs += food.carbs * food.portionCount;
        }
        return carbs;
    }
}
