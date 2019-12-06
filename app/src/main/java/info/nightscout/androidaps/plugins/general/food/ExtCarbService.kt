package info.nightscout.androidaps.plugins.general.food

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.SafeParse

import info.nightscout.androidaps.utils.DateUtil.now
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class ExtCarbService {
    companion object {

        private var CREATED_IN_LAST_MILLIS = 1 * 1000 * 1000L

        fun calculateExtCarb(foodList: List<Food>): Int {
            var eCarb = 0
            for (food in foodList) {
                eCarb += calculateExtCarb(food)
            }
            return eCarb
        }

        fun calculateExtCarb(food: Food): Int {
            val kcalPerOneCarb = 4
            val kcalPerOneFat = 9
            val kcalPerOneProtein = 4

            val eCarb = if (food.energy > 0) {
                SafeParse.stringToDouble(
                        ((food.energy - kcalPerOneCarb * food.carbs) / 10).toString()
                )
            } else {
                SafeParse.stringToDouble(
                        ((food.fat * kcalPerOneFat + food.protein * kcalPerOneProtein) / 10).toString()
                )
            }

            return floor(eCarb * food.portionCount).toInt()
        }

        fun generateExtCarb(newCarb : Int) {
            if (newCarb > 0) {
                var futureTreatments = getFutureTreatmentsFromLastMeals(CREATED_IN_LAST_MILLIS)
                deleteFutureTreatments(futureTreatments)
                var carbCount = getCarbCount(futureTreatments)
                generateExtCarbWrapped(carbCount + newCarb)
            }
        }

        private fun getFutureTreatmentsFromLastMeals(mealsCreatedInLastMillis : Long) : List<Treatment> {
            var treatments = getAllFutureTreatments()
            var times = getCarbTimesFromLastMeals(mealsCreatedInLastMillis)
            return if (treatments.isEmpty() || times.isEmpty()) {
                return emptyList()
            } else {
                times.map { getTreatmentFromCarbTime(it, treatments) }
            }
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

        private fun getCarbTimesFromLastMeals(createdInLastTime : Long) : List<Long> {
            var carbTimes = arrayListOf<Long>()
            CarbsGenerator.getMeals().forEach { mealItem ->
                if (mealItem.date + createdInLastTime > now()) {
                    mealItem.carbTimes.forEach {
                        if (it > now()) {
                            carbTimes.add(it)
                        }
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
            var eCarb = 0.0
            futureTreatments.forEach {
                eCarb += it.carbs
            }
            return eCarb.toInt()
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

        private fun generateExtCarbWrapped(eCarb: Int) {
            ConfigBuilderPlugin.getPlugin().commandQueue.isEcarbEnded = true
            generateExtCarbNow(eCarb)
            ConfigBuilderPlugin.getPlugin().commandQueue.eCarb = 0
        }

        private fun generateExtCarbNow(eCarb: Int) {
            val duration = getDuration(eCarb)
            val extCarbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(Constraint(eCarb)).value()

            val timeOffset = 0
            val time = now() + timeOffset * 1000 * 60

            if (extCarbsAfterConstraints > 0) {
                CarbsGenerator.generateCarbs(extCarbsAfterConstraints!!, time, duration, "")
                NSUpload.uploadEvent(CareportalEvent.NOTE, now() - 2000, MainApp.gs(R.string.generated_ecarbs_note, extCarbsAfterConstraints, duration, timeOffset))
            }
        }

        private fun getDuration(extCarb: Int): Int {
            val wbt = getWBT(extCarb)
            return if (wbt > 4) {
                8
            } else {
                wbt + 2
            }
        }

        private fun getWBT(eCarb: Int): Int {
            return ceil(eCarb.toDouble() / 10.0).toInt()
        }

    }


}
