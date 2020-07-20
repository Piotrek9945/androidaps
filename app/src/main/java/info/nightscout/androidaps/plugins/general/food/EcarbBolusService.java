package info.nightscout.androidaps.plugins.general.food;

import android.content.Context;
import android.text.Html;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.google.common.base.Joiner;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.QuickWizardEntry;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.overview.dialogs.AddFoodMultiplyCorrectionDialog;
import info.nightscout.androidaps.plugins.general.overview.dialogs.AddFoodSensitivityDialog;
import info.nightscout.androidaps.plugins.general.overview.dialogs.TbrDialog;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.BolusWizard;
import info.nightscout.androidaps.utils.OKDialog;

public class EcarbBolusService {

    public static final Double DECREASE_WBT_PERCENTAGE_NO_MOVEMENT = 0.3;
    public static final Double DECREASE_WBT_PERCENTAGE_SMALL_MOVEMENT = 0.5;
    public static final Double DECREASE_WBT_PERCENTAGE_MEDIUM_MOVEMENT = 0.7;
    public static final Double DECREASE_WBT_PERCENTAGE_BIG_MOVEMENT = 1.0;

    public EcarbBolusService() {}

    public static void generateTreatmentWithSummary(Context context, FragmentManager manager, List<Food> foodList, boolean isMultiplyPreSet, boolean isCarbsOnly) {
        if (foodList.size() > 0) {
            List<String> actions = new LinkedList<>();
            for (Food food : foodList) {
                String text = "";
                if (food.name != null && food.units != null) {
                    text = text.concat(food.name);
                    text = text.concat(", " + FoodUtils.Companion.formatFloatToDisplay(food.portion * food.portionCount) + " " + food.units + ", ");
                }
                text = text.concat("eCarbs: " + "<font color='" + MainApp.gc(R.color.carbs) + "'>" + FoodUtils.Companion.roundDoubleToInt(EcarbService.Companion.calculateEcarbs(food)) + "</font>");
                text = text.concat(", węglow.: " + "<font color='" + MainApp.gc(R.color.colorCalculatorButton) + "'>" + FoodUtils.Companion.roundDoubleToInt(BolusService.Companion.calculateCarb(food)) + "</font>");
                actions.add(text);
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            setDialogTitle(builder, isDefaultCorrectionFactor(foodList));
            builder.setMessage(Html.fromHtml(Joiner.on("<br/>").join(actions)));
            builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                synchronized (builder) {
                    if (isMultiplyPreSet == false) {
                        List<Food> foodListClone = FoodService.cloneFoodList(foodList);
                        FoodService.getLastFoodList().clear();
                        FoodService.setLastFoodList(foodListClone);
                    }

                    TempTarget tt = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();
                    if (tt != null && tt.reason != null && tt.reason.equals("Ręczne")) {
                        EcarbBolusService.generateTreatment(context, foodList, isCarbsOnly);
                    } else {
                        showSensitivityDialog(manager, foodList, isCarbsOnly);
                    }
                }
            });
            if (!isMultiplyPreSet) {
                builder.setNeutralButton("KROTNOŚĆ", (dialog, id) -> {
                    synchronized (builder) {
                        showAddFoodPercent(manager, foodList, isCarbsOnly);
                    }
                });
            }
            builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
            builder.show();
        }
    }

    private static void setDialogTitle(AlertDialog.Builder builder, boolean isDefaultCorrectionFactor) {
        if (isDefaultCorrectionFactor) {
            builder.setTitle("Lista posiłków");
        } else {
            builder.setTitle("Lista posiłków (korekta)");
        }
    }

    public static void showAddFoodPercent(FragmentManager manager, List<Food> foodList, boolean isCarbsOnly) {
        new AddFoodMultiplyCorrectionDialog(foodList, isCarbsOnly).show(manager, "AddFoodPercentDialog");
    }

    public static void showSensitivityDialog(FragmentManager manager, List<Food> foodList, boolean isCarbsOnly) {
        new AddFoodSensitivityDialog(foodList, isCarbsOnly).show(manager, "ShowSensitivityDialog");
    }

    public static void generateTreatment(Context context, List<Food> foodList, boolean isCarbsOnly) {
        int eCarbs = EcarbService.Companion.calculateEcarbs(foodList);
        int carbs = BolusService.Companion.calculateCarb(foodList);

        Nutrition nutrition = new Nutrition(carbs, eCarbs);
//        fatProteinImpact(nutrition);
        WBTCorrection(nutrition);

        if (carbs > 0) {
            if (isCarbsOnly) {
                EcarbService.Companion.generateEcarbs(nutrition.getECarbs(), true);
                EcarbService.Companion.generateEcarbs(nutrition.getCarbs(), false);
            } else {
                generateEcarbAndBolus(context, nutrition.getCarbs(), nutrition.getECarbs());
            }
        } else {
            EcarbService.Companion.generateEcarbs(nutrition.getECarbs(), true);
        }

        FoodService.clearFoodCountAdded();
    }

    // w tej konfiguracji więcej szkody niż pożytku...
//    private static void fatProteinImpact(Nutrition nutrition) {
//        int oldCarbs = nutrition.getCarbs();
//        int oldECarbs = nutrition.getECarbs();
//
//        int delta;
//        double ratio = (double) oldECarbs / oldCarbs;
//        if (ratio < 0.2) {
//            delta = 0;
//        } else if (ratio < 0.5) {
//            delta = FoodUtils.Companion.roundDoubleToInt(oldCarbs * 0.1);
//        } else if (ratio < 1) {
//            delta = FoodUtils.Companion.roundDoubleToInt(oldCarbs * 0.3);
//        } else if (ratio < 3) {
//            delta = FoodUtils.Companion.roundDoubleToInt(oldCarbs * 0.6);
//        } else {
//            delta = oldCarbs;
//        }
//
//        int newCarbs = oldCarbs - delta;
//        int newECarbs = oldECarbs + delta;
//
//        nutrition.setCarbs(newCarbs);
//        nutrition.setECarbs(newECarbs);
//    }

        private static void WBTCorrection(Nutrition nutrition) {
        int oldECarbs = nutrition.getECarbs();
        int newECarbs;

        TempTarget tt = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();
        if (tt != null && tt.reason != null && tt.reason.equals("Ręczne") && tt.low == TbrDialog.TBR_PERCENTAGE_1) {
            // mały ruch
            newECarbs = FoodUtils.Companion.roundDoubleToInt(oldECarbs * (1 - EcarbBolusService.DECREASE_WBT_PERCENTAGE_SMALL_MOVEMENT));
        } else if (tt != null && tt.reason != null && tt.reason.equals("Ręczne") && tt.low == TbrDialog.TBR_PERCENTAGE_2) {
            // średni ruch
            newECarbs = FoodUtils.Companion.roundDoubleToInt(oldECarbs * (1 - EcarbBolusService.DECREASE_WBT_PERCENTAGE_MEDIUM_MOVEMENT));
        } else if (tt != null && tt.reason != null && tt.reason.equals("Ręczne") && tt.low == TbrDialog.TBR_PERCENTAGE_3) {
            // duży ruch
            newECarbs = FoodUtils.Companion.roundDoubleToInt(oldECarbs * (1 - EcarbBolusService.DECREASE_WBT_PERCENTAGE_BIG_MOVEMENT));
        } else {
            // brak ruchu
            newECarbs = FoodUtils.Companion.roundDoubleToInt(oldECarbs * (1 - EcarbBolusService.DECREASE_WBT_PERCENTAGE_NO_MOVEMENT));
        }
        nutrition.setECarbs(newECarbs);
    }

    public static void generateEcarbAndBolus(Context context, int carbs, int eCarbs) {
        try {
            BolusWizard wizard = onClickQuickwizard(context, carbs);
            wizard.confirmAndExecute(context, eCarbs);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static BolusWizard onClickQuickwizard(Context context, Integer carbs) throws JSONException {
        final Profile profile = ProfileFunctions.getInstance().getProfile();
        final String profileName = ProfileFunctions.getInstance().getProfileName();
        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        JSONObject input = new JSONObject("{\"buttonText\":\"\",\"carbs\":" + carbs + ",\"validFrom\":0,\"validTo\":86340, \"useBG\":1, \"useBolusIOB\":1, \"useBasalIOB\":1}");
        final QuickWizardEntry quickWizardEntry = new QuickWizardEntry(input, -1);
        if (quickWizardEntry != null && profile != null && pump != null) {
            final BolusWizard wizard = quickWizardEntry.doCalc(profile, profileName, null, true);

            if (wizard.getCalculatedTotalInsulin() > 0d && quickWizardEntry.carbs() > 0d) {
                Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(quickWizardEntry.carbs())).value();

                if (Math.abs(wizard.getInsulinAfterConstraints() - wizard.getCalculatedTotalInsulin()) >= pump.getPumpDescription().pumpType.determineCorrectBolusStepSize(wizard.getInsulinAfterConstraints()) || !carbsAfterConstraints.equals(quickWizardEntry.carbs())) {
                    OKDialog.show(context, MainApp.gs(R.string.treatmentdeliveryerror), MainApp.gs(R.string.constraints_violation) + "\n" + MainApp.gs(R.string.changeyourinput), null);
                    return wizard;
                }

                return wizard;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static boolean isDefaultCorrectionFactor(Food food) {
        if (food.correctionFactor == 1.0) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean isDefaultCorrectionFactor(List<Food> foodList) {
        for (Food food : foodList) {
            if (isDefaultCorrectionFactor(food) == false) {
                return false;
            }
        }
        return true;
    }

}
