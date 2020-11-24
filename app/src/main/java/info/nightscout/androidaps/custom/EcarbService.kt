package info.nightscout.androidaps.custom

import info.nightscout.androidaps.plugins.general.food.Food
import info.nightscout.androidaps.utils.SafeParse

class EcarbService {

    companion object {

        private var RECENTLY_CREATED_MILLIS = 30 * 60 * 1000L
        @JvmStatic val ECARB_TIME_OFFSET_MINS = 15

        fun calculateEcarbs(foodList: List<Food>): Int {
            var eCarbs = 0.0
            for (food in foodList) {
                eCarbs += calculateEcarbs(food)
            }
            return FoodUtils.roundDoubleToInt(eCarbs)
        }

        fun calculateEcarbs(food: Food): Double {
            val kcalPerOneCarb = 4
            val kcalPerOneFat = 9
            val kcalPerOneProtein = 4

            val eCarbs = if (food.energy > 0) {
                SafeParse.stringToDouble(
                    ((food.energy - kcalPerOneCarb * food.carbs) / 10).toString()
                )
            } else {
                SafeParse.stringToDouble(
                    ((food.fat * kcalPerOneFat + food.protein * kcalPerOneProtein) / 10).toString()
                )
            }

            val returnEcarbs = eCarbs * food.portionCount * food.correctionFactor * food.eCarbCorrection * food.sensitivityFactor
            return if (returnEcarbs < 0.0) {
                0.0
            } else {
                returnEcarbs
            }
        }

    }

}