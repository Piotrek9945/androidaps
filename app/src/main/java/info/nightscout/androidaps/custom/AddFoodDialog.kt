package info.nightscout.androidaps.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import com.google.common.base.Joiner
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.food.Food
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.formatColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import kotlinx.android.synthetic.main.custom_add_food_dialog.*
import kotlinx.android.synthetic.main.dialog_carbs.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class AddFoodDialog(private val food: Food, private val isLastMeal: Boolean) : DaggerDialogFragment() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var carbsGenerator: CarbsGenerator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.custom_add_food_dialog, container, false)
    }

    fun onCreateViewGeneral() {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val foodList:MutableList<Food> = if (isLastMeal) {
            FoodData.lastFoodList
        } else {
            FoodData.foodList
        }

        val picker = custom_add_food_dialog_edit_count
        picker.setParams(1.0, 0.1, 99999.0, 0.1, DecimalFormat("0.0"), false, ok, null)

        custom_add_food_dialog_decrement_button.setOnClickListener {
            picker.value--
        }

        custom_add_food_dialog_increment_button.setOnClickListener {
            picker.value++
        }

        if (isLastMeal) {
            val lastMeal = custom_add_food_dialog_last_meal_text
            lastMeal.visibility = View.VISIBLE
            val sb = StringBuilder()
            foodList.forEach {
                if (it.portion == 1.0) {
                    sb.append(it.name + ", " + FoodUtils.formatFloatToDisplay(it.portionCount) + " " + it.units + "\n")
                } else {
                    sb.append(it.name + ", " + FoodUtils.formatFloatToDisplay(it.portionCount) + " " + "x" + " " + FoodUtils.formatFloatToDisplay(it.portion) + " " + it.units + "\n")
                }
            }
            lastMeal.text = sb.toString()
        }

        val isCarbsOnly = custom_add_food_dialog_is_carbs_only
        isCarbsOnly.visibility = if (isLastMeal) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    fun submit(): Boolean {
        val carbs = overview_carbs_carbs.value.toInt()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()
        val units = profileFunction.getUnits()
        val activityTTDuration = defaultValueHelper.determineActivityTTDuration()
        val activityTT = defaultValueHelper.determineActivityTT()
        val eatingSoonTTDuration = defaultValueHelper.determineEatingSoonTTDuration()
        val eatingSoonTT = defaultValueHelper.determineEatingSoonTT()
        val hypoTTDuration = defaultValueHelper.determineHypoTTDuration()
        val hypoTT = defaultValueHelper.determineHypoTT()
        val actions: LinkedList<String?> = LinkedList()
        val unitLabel = if (units == Constants.MMOL) resourceHelper.gs(R.string.mmol) else resourceHelper.gs(R.string.mgdl)

        val activitySelected = overview_carbs_activity_tt.isChecked
        if (activitySelected)
            actions.add(resourceHelper.gs(R.string.temptargetshort) + ": " + (DecimalFormatter.to1Decimal(activityTT) + " " + unitLabel + " (" + resourceHelper.gs(R.string.format_mins, activityTTDuration) + ")").formatColor(resourceHelper, R.color.tempTargetConfirmation))
        val eatingSoonSelected = overview_carbs_eating_soon_tt.isChecked
        if (eatingSoonSelected)
            actions.add(resourceHelper.gs(R.string.temptargetshort) + ": " + (DecimalFormatter.to1Decimal(eatingSoonTT) + " " + unitLabel + " (" + resourceHelper.gs(R.string.format_mins, eatingSoonTTDuration) + ")").formatColor(resourceHelper, R.color.tempTargetConfirmation))
        val hypoSelected = overview_carbs_hypo_tt.isChecked
        if (hypoSelected)
            actions.add(resourceHelper.gs(R.string.temptargetshort) + ": " + (DecimalFormatter.to1Decimal(hypoTT) + " " + unitLabel + " (" + resourceHelper.gs(R.string.format_mins, hypoTTDuration) + ")").formatColor(resourceHelper, R.color.tempTargetConfirmation))

        val timeOffset = overview_carbs_time.value.toInt()
        // eventTime -= eventTime % 1000
        // val time = eventTime + timeOffset * 1000 * 60
        // if (timeOffset != 0)
        //     actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(time))
        // val duration = overview_carbs_duration.value.toInt()
        // if (duration > 0)
        //     actions.add(resourceHelper.gs(R.string.duration) + ": " + duration + resourceHelper.gs(R.string.shorthour))
        // if (carbsAfterConstraints > 0) {
        //     actions.add(resourceHelper.gs(R.string.carbs) + ": " + "<font color='" + resourceHelper.gc(R.color.carbs) + "'>" + resourceHelper.gs(R.string.format_carbs, carbsAfterConstraints) + "</font>")
        //     if (carbsAfterConstraints != carbs)
        //         actions.add("<font color='" + resourceHelper.gc(R.color.warning) + "'>" + resourceHelper.gs(R.string.carbsconstraintapplied) + "</font>")
        // }
        // val notes = notes.text.toString()
        // if (notes.isNotEmpty())
        //     actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
        //
        // if (eventTimeChanged)
        //     actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        if (carbsAfterConstraints > 0 || activitySelected || eatingSoonSelected || hypoSelected) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.carbs), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    when {
                        activitySelected   -> {
                            aapsLogger.debug("USER ENTRY: TEMPTARGET ACTIVITY $activityTT duration: $activityTTDuration")
                            val tempTarget = TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(activityTTDuration)
                                .reason(resourceHelper.gs(R.string.activity))
                                .source(Source.USER)
                                .low(Profile.toMgdl(activityTT, profileFunction.getUnits()))
                                .high(Profile.toMgdl(activityTT, profileFunction.getUnits()))
                            treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                        }

                        eatingSoonSelected -> {
                            aapsLogger.debug("USER ENTRY: TEMPTARGET EATING SOON $eatingSoonTT duration: $eatingSoonTTDuration")
                            val tempTarget = TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(eatingSoonTTDuration)
                                .reason(resourceHelper.gs(R.string.eatingsoon))
                                .source(Source.USER)
                                .low(Profile.toMgdl(eatingSoonTT, profileFunction.getUnits()))
                                .high(Profile.toMgdl(eatingSoonTT, profileFunction.getUnits()))
                            treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                        }

                        hypoSelected       -> {
                            aapsLogger.debug("USER ENTRY: TEMPTARGET HYPO $hypoTT duration: $hypoTTDuration")
                            val tempTarget = TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(hypoTTDuration)
                                .reason(resourceHelper.gs(R.string.hypo))
                                .source(Source.USER)
                                .low(Profile.toMgdl(hypoTT, profileFunction.getUnits()))
                                .high(Profile.toMgdl(hypoTT, profileFunction.getUnits()))
                            treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                        }
                    }
                    if (carbsAfterConstraints > 0) {
                        // if (duration == 0) {
                        //     aapsLogger.debug("USER ENTRY: CARBS $carbsAfterConstraints time: $time")
                        //     carbsGenerator.createCarb(carbsAfterConstraints, time, CareportalEvent.CARBCORRECTION, notes)
                        // } else {
                        //     aapsLogger.debug("USER ENTRY: CARBS $carbsAfterConstraints time: $time duration: $duration")
                        //     carbsGenerator.generateCarbs(carbsAfterConstraints, time, duration, notes)
                        //     nsUpload.uploadEvent(CareportalEvent.NOTE, DateUtil.now() - 2000, resourceHelper.gs(R.string.generated_ecarbs_note, carbsAfterConstraints, duration, timeOffset))
                        // }
                    }
                }, null)
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, resourceHelper.gs(R.string.carbs), resourceHelper.gs(R.string.no_action_selected))
            }
        return true
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage)
        }
    }

}