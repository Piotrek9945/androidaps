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

    }

}
