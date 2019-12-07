package info.nightscout.androidaps.plugins.general.food

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.general.food.FoodUtils.Companion.roundDoubleToInt
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator
import info.nightscout.androidaps.utils.SafeParse

import info.nightscout.androidaps.utils.DateUtil.now
import kotlin.math.round

class CarbService {
    companion object {

        fun calculateCarb(foodList: List<Food>): Int {
            var carbs = 0.0
            for (food in foodList) {
                carbs += food.carbs * food.portionCount
            }
            return roundDoubleToInt(carbs)
        }
    }
}
