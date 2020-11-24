package info.nightscout.androidaps.custom

import info.nightscout.androidaps.plugins.general.food.Food
import org.apache.commons.lang3.SerializationUtils.clone
import java.util.*

class FoodData {

    companion object {
        val foodList: MutableList<Food> = mutableListOf()
        val lastFoodList: MutableList<Food> = mutableListOf()

        fun cloneFood(food: Food): Food {
            return clone(food)
        }

        fun cloneFoodList(foodList: List<Food>): List<Food> {
            val newFoodList: MutableList<Food> = ArrayList()
            foodList.forEach {
                newFoodList.add(cloneFood(it))
            }
            return newFoodList
        }

        fun setLastFoodList(foodList: List<Food>) {
            lastFoodList.clear()
            lastFoodList.addAll(cloneFoodList(foodList))
        }
    }

}