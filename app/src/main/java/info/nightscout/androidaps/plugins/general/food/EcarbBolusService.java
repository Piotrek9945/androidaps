package info.nightscout.androidaps.plugins.general.food;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.QuickWizardEntry;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.utils.BolusWizard;
import info.nightscout.androidaps.utils.OKDialog;

public class EcarbBolusService {

    public EcarbBolusService() {}

    public static void generateTreatment(Context context, List<Food> foodList) {
        int eCarbs = EcarbService.Companion.calculateEcarbs(foodList);
        int carbs = CarbService.Companion.calculateCarb(foodList);

        if (carbs > 0) {
            generateEcarbAndBolus(context, carbs, eCarbs);
        } else {
            EcarbService.Companion.generateEcarbs(eCarbs);
        }

        FoodService.clearFoodCountAdded();
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

}
