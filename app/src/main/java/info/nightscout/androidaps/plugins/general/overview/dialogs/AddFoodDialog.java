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
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.Food;
import info.nightscout.androidaps.plugins.general.food.FoodService;
import info.nightscout.androidaps.utils.NumberPicker;

public class AddFoodDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(AddFoodDialog.class);

    private final Food food;

    private NumberPicker editCount;
    private TextView summary;

    //one shot guards
    private boolean okClicked;

    public AddFoodDialog(Food food) {
        this.food = food;
    }

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            setSummaryText();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_addfood_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        editCount = view.findViewById(R.id.addfood_edit_count);
        editCount.setParams(1d, 1d, 99999d, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), textWatcher);

        summary = view.findViewById(R.id.addfood_summary);
        setSummaryText();

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);

        //recovering state if there is something
        if (savedInstanceState != null) {
            editCount.setValue(savedInstanceState.getDouble("editCount"));
        }
        return view;
    }

    private void setSummaryText() {
        Double result = food.portion * editCount.getValue();
        summary.setText("Ilość: " + result.intValue() + " [" + food.units + "]");
    }

    @Override
    public void onSaveInstanceState(Bundle carbsDialogState) {
        carbsDialogState.putDouble("editCount", editCount.getValue());
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
            addFood(editCount);
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void addFood(NumberPicker editCount) {
        double count = editCount.getValue().doubleValue();
        if (count > 0) {
            Food foodCopy = cloneFood();
            multiplyCountByPortions(foodCopy, count);
            FoodService.addFoodToList(foodCopy);
            FoodService.updateFoodCountAdded();
        }
    }

    private Food cloneFood() {
        return SerializationUtils.clone(food);
    }

    private Food multiplyCountByPortions(Food food, double count) {
        food.portionCount *= count;
        return food;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
