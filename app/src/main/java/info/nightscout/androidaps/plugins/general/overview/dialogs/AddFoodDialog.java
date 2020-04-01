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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.EcarbBolusService;
import info.nightscout.androidaps.plugins.general.food.Food;
import info.nightscout.androidaps.plugins.general.food.FoodService;
import info.nightscout.androidaps.utils.NumberPicker;

public class AddFoodDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(AddFoodDialog.class);

    private final Food food;
    private final boolean isLastMeal;

    private NumberPicker editCount;
    private Button floatDecrementButton;
    private Button floatIncrementButton;
    private TextView lastMealText;
    private Button otherSettingsButton;
    private NumberPicker eCarbCorrection;
    private TextView eCarbCorrectionText;
    private final String SHOW_OTHER_SETTINGS = "POKAŻ INNE USTAWIENIA";
    private final String HIDE_OTHER_SETTINGS = "UKRYJ INNE USTAWIENIA";

    //one shot guards
    private boolean okClicked;

    public AddFoodDialog(Food food, boolean isLastMeal) {
        this.food = food;
        this.isLastMeal = isLastMeal;
    }

    final private TextWatcher editCountTextWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    final private TextWatcher eCarbCorrectionTextWatcher = new TextWatcher() {


        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        List<Food> foodList = FoodService.getFoodList();
        View view = inflater.inflate(R.layout.overview_addfood_dialog, container, false);

        view.findViewById(R.id.mdtp_ok).setOnClickListener(this);
        view.findViewById(R.id.mdtp_cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        editCount = view.findViewById(R.id.addfood_edit_count);
        editCount.setParams(1d, 0.1d, 99999d, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.mdtp_ok), editCountTextWatcher);

        floatDecrementButton = view.findViewById(R.id.decrement_button);
        floatDecrementButton.setOnClickListener(this);

        floatIncrementButton = view.findViewById(R.id.increment_button);
        floatIncrementButton.setOnClickListener(this);

        if (isLastMeal == true) {
            lastMealText = view.findViewById(R.id.last_meal_text);
            lastMealText.setVisibility(View.VISIBLE);
            lastMealText.setText(food.name + ", " + food.portion + " " + food.units);
        }

        otherSettingsButton = view.findViewById(R.id.other_settings_button);
        otherSettingsButton.setOnClickListener(this);
        otherSettingsButton.setText(SHOW_OTHER_SETTINGS);

        eCarbCorrection = view.findViewById(R.id.addfood_ecarb_correction);
        eCarbCorrection.setParams(getEcarbCorrectionInitVal(), 10d, 200d, 10d, new DecimalFormat("0"), false, view.findViewById(R.id.mdtp_ok), eCarbCorrectionTextWatcher);
        eCarbCorrection.setOnClickListener(this);
        if (FoodService.isAddedYet(food, foodList)) {
            Food foodFromList = FoodService.getFoodFromListWithTheSameId(food._id, foodList);
            eCarbCorrection.setValue(foodFromList.eCarbCorrection * 100);
            disableEcarbCorrectionPicker();
        }
        eCarbCorrectionText = view.findViewById(R.id.addfood_ecarb_correction_text);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);

        //recovering state if there is something
        if (savedInstanceState != null) {
            editCount.setValue(savedInstanceState.getDouble("editCount"));
        }
        return view;
    }

    private double getEcarbCorrectionInitVal() {
        if (isLastMeal == true) {
            return food.eCarbCorrection * 100;
        } else {
            return 100d;
        }
    }

    private void disableEcarbCorrectionPicker() {
        double oldValue = eCarbCorrection.getValue();
        eCarbCorrection.setOnValueChangedListener(value -> {
            if (eCarbCorrection.getValue() == oldValue) {} else {
                eCarbCorrection.setValue(oldValue);
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle carbsDialogState) {
        carbsDialogState.putDouble("editCount", editCount.getValue());
        super.onSaveInstanceState(carbsDialogState);
    }


    @Override
    public synchronized void onClick(View view) {
        switch (view.getId()) {
            case R.id.mdtp_ok:
                submit();
                break;
            case R.id.mdtp_cancel:
                dismiss();
                break;
            case R.id.decrement_button:
                changePickerValue(-1);
                break;
            case R.id.increment_button:
                changePickerValue(+1);
                break;
            case R.id.other_settings_button:
                if (eCarbCorrection.getVisibility() == View.VISIBLE) {
                    setOtherSettingsVisibility(View.GONE);
                    otherSettingsButton.setText(SHOW_OTHER_SETTINGS);
                } else {
                    setOtherSettingsVisibility(View.VISIBLE);
                    otherSettingsButton.setText(HIDE_OTHER_SETTINGS);
                }
        }
    }

    private void setOtherSettingsVisibility(int visibility) {
        eCarbCorrectionText.setVisibility(visibility);
        eCarbCorrection.setVisibility(visibility);
    }

    private void changePickerValue(double changeValue) {
        double oldValue = editCount.getValue();
        double newValue = oldValue + changeValue;
        editCount.setValue(newValue);
    }

    private void submit() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;
        try {
            double count = editCount.getValue().doubleValue();
            if (count > 0) {
                double eCarbCorrection = this.eCarbCorrection.getValue() / 100;
                if (isLastMeal == true) {
                    prepareLastMeal(count, eCarbCorrection);
                } else {
                    prepareFoodList(count, eCarbCorrection);
                }
            }
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void prepareFoodList(double count,  double eCarbCorrection) {
        Food foodCopy = FoodService.cloneFood(food);
        foodCopy.eCarbCorrection = eCarbCorrection;
        multiplyCountByPortions(foodCopy, count);
        FoodService.setLastFood(foodCopy);
        FoodService.addFoodToList(foodCopy);
        FoodService.updateFoodCountAdded();
    }

    private void prepareLastMeal(double count, double eCarbCorrection) {
        Food foodCopy = FoodService.cloneFood(food);
        foodCopy.eCarbCorrection = eCarbCorrection;
        foodCopy.portionCount = 1;
        multiplyCountByPortions(foodCopy, count);
        EcarbBolusService.generateTreatmentWithSummary(getContext(), getFragmentManager(), Collections.singletonList(foodCopy));
    }

    private void multiplyCountByPortions(Food food, double count) {
        food.portionCount *= count;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
