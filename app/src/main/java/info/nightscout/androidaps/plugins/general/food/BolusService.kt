package info.nightscout.androidaps.plugins.general.food

import info.nightscout.androidaps.plugins.general.food.FoodUtils.Companion.roundDoubleToInt

class BolusService {
    companion object {

        fun calculateCarb(food : Food) : Double {
            return food.carbs * food.portionCount * food.correctionFactor * food.sensitivityFactor
        }

        fun calculateCarb(foodList: List<Food>): Int {
            var carbs = 0.0
            for (food in foodList) {
                carbs += calculateCarb(food)
            }
            return roundDoubleToInt(carbs)
        }
    }
}
