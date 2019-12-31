package info.nightscout.androidaps.plugins.general.food

import java.text.DecimalFormat
import kotlin.math.round

class FoodUtils {

    companion object {

        fun formatFloatToDisplay(number: Double): String {
            val df = DecimalFormat("0.0")
            return if (number % 1 == 0.0) {
                number.toInt().toString()
            } else {
                df.format(number)
            }
        }

        fun roundDoubleToInt(number: Double) : Int {
            return round(number).toInt()
        }

        fun setFoodAccurateParam(food: Food, accurate: Boolean) {
            if (accurate) {
                food.accurateCarbCorrection = 1.0
                food.accurateEcarbCorrection = 1.0
            } else {
                food.accurateCarbCorrection = BolusService.ACCURATE_CARB_COEFFICIENT
                food.accurateEcarbCorrection = EcarbService.ACCURATE_ECARB_COEFFICIENT
            }
        }
    }

}
