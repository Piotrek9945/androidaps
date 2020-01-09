package info.nightscout.androidaps.plugins.general.food

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.MealCarb
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil.now
import info.nightscout.androidaps.utils.SafeParse
import java.util.*
import kotlin.math.abs

class EcarbService {
    companion object {

        const val ACCURATE_ECARB_COEFFICIENT = 1.0
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

            val returnEcarbs = eCarbs * food.portionCount * food.correctionFactor * food.accurateEcarbCorrection * food.eCarbCorrection * food.sensitivityFactor
            return if (returnEcarbs < 0.0) {
                0.0
            } else {
                returnEcarbs
            }
        }

        fun generateEcarbs(newEcarbs : Int) {
            if (newEcarbs > 0) {
                var oldEcarbs = getCountAndRemoveNotAbsorbedEcarbsFromLastMeals()
                generateEcarbWrapped(oldEcarbs + newEcarbs)
            }
        }
        
        private fun getCountAndRemoveNotAbsorbedEcarbsFromLastMeals() : Int {
            var futureTreatments = getNotAbsorbedEcarbsFromLastMeals()
            var oldEcarbs = getCarbCount(futureTreatments)
            removeFutureTreatmentsAndMeals(futureTreatments)
            return oldEcarbs
        }

        private fun getNotAbsorbedEcarbsFromLastMeals() : List<Treatment> {
            var treatments = getAllFutureTreatments()
            var meals = getRecentlyCreatedMeals()
            var times = getNotAbsorbedCarbTimes(meals)

            return if (treatments.isEmpty() || times.isEmpty()) {
                return emptyList()
            } else {
                times.map { getTreatmentFromCarbTime(it, treatments) }
            }
        }

        private fun removeFutureTreatmentsAndMeals(futureTreatments: List<Treatment>) {
            deleteFutureTreatments(futureTreatments)
            deleteMeals(getRecentlyCreatedMeals())
        }

        private fun getAllFutureTreatments() : List<Treatment> {
            var treatments = TreatmentsPlugin.getPlugin().service.getTreatmentDataFromTime(now() + 1000, true)
            return filterTreatments(treatments)
        }

        private fun filterTreatments(treatments: List<Treatment>): List<Treatment> {
            return treatments.filter {
                it.source == Source.USER && it.insulin == 0.0
            }
        }

        private fun getTreatmentFromCarbTime(date : Long, treatments: List<Treatment>) : Treatment {
            return Collections.min(treatments) { t1, t2 ->
                when {
                    (abs(t1.date - date) > abs(t2.date - date)) -> 1
                    (abs(t1.date - date) < abs(t2.date - date)) -> -1
                    else -> 0
                }
            }
        }

        private fun getRecentlyCreatedMeals() : List<MealCarb> {
            var meals = arrayListOf<MealCarb>()
            CarbsGenerator.getMeals().forEach { mealItem ->
                if (mealItem.date + RECENTLY_CREATED_MILLIS > now()) {
                    meals.add(mealItem)
                }
            }
            return meals
        }
        
        private fun deleteMeals(meals : List<MealCarb>) {
            meals.forEach {
                CarbsGenerator.getMeals().remove(it)
            }
        }

        private fun getNotAbsorbedCarbTimes(meals : List<MealCarb>) : List<Long> {
            var carbTimes = arrayListOf<Long>()
            meals.forEach { meal: MealCarb ->
                meal.carbTimes.forEach { time ->
                    if (time > now()) {
                        carbTimes.add(time)
                    }
                }
            }
            return carbTimes
        }

        private fun getCarbCount(futureTreatments: List<Treatment>): Int {
            return if (futureTreatments.isEmpty()) {
                0
            } else {
                return getTreatmentCarbCount(futureTreatments)
            }
        }

        private fun getTreatmentCarbCount(futureTreatments : List<Treatment>) : Int {
            var eCarbs = 0.0
            futureTreatments.forEach {
                eCarbs += it.carbs
            }
            return eCarbs.toInt()
        }

        private fun deleteFutureTreatments(futureTreatments : List<Treatment>) {
            for (treatment in futureTreatments) {
                val _id = treatment._id
                if (NSUpload.isIdValid(_id)) {
                    NSUpload.removeCareportalEntryFromNS(_id)
                } else {
                    UploadQueue.removeID("dbAdd", _id)
                }
                TreatmentsPlugin.getPlugin().service.delete(treatment)
            }
        }

        private fun generateEcarbWrapped(eCarbs: Int) {
            ConfigBuilderPlugin.getPlugin().commandQueue.isEcarbEnded = true
            generateEcarbNow(eCarbs)
            ConfigBuilderPlugin.getPlugin().commandQueue.eCarbs = 0
        }

        private fun generateEcarbNow(eCarbs: Int) {
            val duration = getDuration(eCarbs)
            val eCarbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(Constraint(eCarbs)).value()

            val time = now() + ECARB_TIME_OFFSET_MINS * 1000 * 60

            if (eCarbsAfterConstraints > 0) {
                CarbsGenerator.generateCarbs(eCarbsAfterConstraints, time, duration, "")
                NSUpload.uploadEvent(CareportalEvent.NOTE, now() - 2000, MainApp.gs(R.string.generated_ecarbs_note, eCarbsAfterConstraints, duration, ECARB_TIME_OFFSET_MINS))
            }
        }

        private fun getDuration(eCarbs: Int): Int {
            val wbt = getWBT(eCarbs)
            return when {
                wbt > 5 -> 7
                wbt > 2 -> wbt + 1
                else -> wbt + 2
            }
        }

        private fun getWBT(eCarbs: Int): Int {
            return FoodUtils.roundDoubleToInt(eCarbs / 10.0)
        }

    }


}
