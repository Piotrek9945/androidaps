package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.fragment.app.DialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.EcarbBolusService;
import info.nightscout.androidaps.plugins.general.food.Food;
import info.nightscout.androidaps.plugins.general.food.FoodService;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SpinnerHelper;

public class AddFoodSensitivityDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(AddFoodSensitivityDialog.class);

    private final Double SENSITIVITY_FACTOR_GRADE_1 = 1.0;
    private final Double SENSITIVITY_FACTOR_GRADE_2 = 0.9;
    private final Double SENSITIVITY_FACTOR_GRADE_3 = 0.8;
    private final Double SENSITIVITY_FACTOR_GRADE_4 = 0.7;
    private final Double SENSITIVITY_FACTOR_GRADE_5 = 0.6;

    private List<Food> foodList;

    private RadioGroup sensitivityRadioGroup;

    //one shot guards
    private boolean okClicked;

    public AddFoodSensitivityDialog(List<Food> foodList) {
        this.foodList = foodList;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_addfood_sensitivity_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        sensitivityRadioGroup = view.findViewById(R.id.addfood_sensitivity);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);

        //recovering state if there is something
        if (savedInstanceState != null) {
        }
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle carbsDialogState) {
        super.onSaveInstanceState(carbsDialogState);
    }


    @Override
    public synchronized void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                submit();
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

    private void submit() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;
        try {
            setSensitivityFactor(foodList);
            EcarbBolusService.generateTreatment(getContext(), foodList);
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void setSensitivityFactor(List<Food> foodList) {
        Double sensitivityFactor = getSensitivityFactor(getSelectedSensitivity());
        for (Food food : foodList) {
            food.sensitivityFactor = sensitivityFactor;
        }
    }

    private Integer getSelectedSensitivity() {
        RadioButton selectedSensitivity = getView().findViewById(sensitivityRadioGroup.getCheckedRadioButtonId());
        String sensitivityTextValue = selectedSensitivity.getText().toString();
        return Integer.parseInt(sensitivityTextValue);
    }

    private Double getSensitivityFactor(Integer sensitivityRadioButtonTextValue) {
        switch (sensitivityRadioButtonTextValue) {
            case 1: return SENSITIVITY_FACTOR_GRADE_1;
            case 2: return SENSITIVITY_FACTOR_GRADE_2;
            case 3: return SENSITIVITY_FACTOR_GRADE_3;
            case 4: return SENSITIVITY_FACTOR_GRADE_4;
            case 5: return SENSITIVITY_FACTOR_GRADE_5;
            default: throw new NullPointerException();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
