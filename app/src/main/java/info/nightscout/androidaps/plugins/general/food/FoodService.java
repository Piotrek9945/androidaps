package info.nightscout.androidaps.plugins.general.food;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteBaseService;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.apache.commons.lang3.SerializationUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ICallback;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventFoodDatabaseChanged;
import info.nightscout.androidaps.events.EventNsFood;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static info.nightscout.androidaps.plugins.general.food.FoodFragment.foodCountAdded;

/**
 * Created by mike on 24.09.2017.
 */

public class FoodService extends OrmLiteBaseService<DatabaseHelper> {
    private Logger log = LoggerFactory.getLogger(L.DATAFOOD);
    private CompositeDisposable disposable = new CompositeDisposable();

    private static final ScheduledExecutorService foodEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledFoodEventPost = null;

    private static List<Food> foodList = new ArrayList<>();

    public static Food lastFood;

    public static void addFoodToList(Food food) {
        boolean isAddedYet = false;
        for(Food item : foodList) {
            if (item._id.equals(food._id)) {
                item.portionCount += food.portionCount;
                isAddedYet = true;
                break;
            }
        }
        if (isAddedYet == false) {
            foodList.add(food);
        }
    }

    public static List<Food> getFoodList() { return FoodService.foodList; }

    public static void clearFoodCountAdded() {
        foodList.clear();
        updateFoodCountAdded();
    }

    public static void updateFoodCountAdded() {
        FoodFragment.foodCountAdded.setText(String.valueOf(getFoodList().size()));
    }

    public static Food cloneFood(Food food) {
        return SerializationUtils.clone(food);
    }

    public static List<Food> cloneFoodList(List<Food> foodList) {
        List<Food> newFoodList = new ArrayList<>();
        for (Food food : foodList) {
            newFoodList.add(FoodService.cloneFood(food));
        }
        return newFoodList;
    }

    public FoodService() {
        onCreate();
        dbInitialize();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNsFood.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    int mode = event.getMode();
                    Bundle payload = event.getPayload();

                    try {
                        if (payload.containsKey("food")) {
                            JSONObject json = new JSONObject(payload.getString("food"));
                            if (mode == EventNsFood.Companion.getADD() || mode == EventNsFood.Companion.getUPDATE())
                                this.createFoodFromJsonIfNotExists(json);
                            else
                                this.deleteNS(json);
                        }

                        if (payload.containsKey("foods")) {
                            JSONArray array = new JSONArray(payload.getString("foods"));
                            if (mode == EventNsFood.Companion.getADD() || mode == EventNsFood.Companion.getUPDATE())
                                this.createFoodFromJsonIfNotExists(array);
                            else
                                this.deleteNS(array);
                        }
                    } catch (JSONException e) {
                        log.error("Unhandled Exception", e);
                    }
                }, FabricPrivacy::logException)
        );
    }

    /**
     * This method is a simple re-implementation of the database create and up/downgrade functionality
     * in SQLiteOpenHelper#getDatabaseLocked method.
     * <p>
     * It is implemented to be able to late initialize separate plugins of the application.
     */
    protected void dbInitialize() {
        DatabaseHelper helper = OpenHelperManager.getHelper(this, DatabaseHelper.class);
        int newVersion = helper.getNewVersion();
        int oldVersion = helper.getOldVersion();

        if (oldVersion > newVersion) {
            onDowngrade(this.getConnectionSource(), oldVersion, newVersion);
        } else {
            onUpgrade(this.getConnectionSource(), oldVersion, newVersion);
        }
    }

    public Dao<Food, Long> getDao() {
        try {
            return DaoManager.createDao(this.getConnectionSource(), Food.class);
        } catch (SQLException e) {
            log.error("Cannot create Dao for Food.class");
        }

        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            if (L.isEnabled(L.DATAFOOD))
                log.info("onCreate");
            TableUtils.createTableIfNotExists(this.getConnectionSource(), Food.class);
        } catch (SQLException e) {
            log.error("Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    public void onUpgrade(ConnectionSource connectionSource, int oldVersion, int newVersion) {
        if (L.isEnabled(L.DATAFOOD))
            log.info("onUpgrade");
//            this.resetFood();
    }

    public void onDowngrade(ConnectionSource connectionSource, int oldVersion, int newVersion) {
        // this method is not supported right now
    }

    public void resetFood() {
        try {
            TableUtils.dropTable(this.getConnectionSource(), Food.class, true);
            TableUtils.createTableIfNotExists(this.getConnectionSource(), Food.class);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleFoodChange();
    }


    /**
     * A place to centrally register events to be posted, if any data changed.
     * This should be implemented in an abstract service-class.
     * <p>
     * We do need to make sure, that ICallback is extended to be able to handle multiple
     * events, or handle a list of events.
     * <p>
     * on some methods the earliestDataChange event is handled separatly, in that it is checked if it is
     * set to null by another event already (eg. scheduleExtendedBolusChange).
     *
     * @param event
     * @param eventWorker
     * @param callback
     */
    private void scheduleEvent(final Event event, ScheduledExecutorService eventWorker,
                               final ICallback callback) {

        class PostRunnable implements Runnable {
            public void run() {
                if (L.isEnabled(L.DATAFOOD))
                    log.debug("Firing EventFoodChange");
                RxBus.INSTANCE.send(event);
                callback.setPost(null);
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (callback.getPost() != null)
            callback.getPost().cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        callback.setPost(eventWorker.schedule(task, sec, TimeUnit.SECONDS));
    }

    /**
     * Schedule a foodChange Event.
     */
    public void scheduleFoodChange() {
        this.scheduleEvent(new EventFoodDatabaseChanged(), foodEventWorker, new ICallback() {
            @Override
            public void setPost(ScheduledFuture<?> post) {
                scheduledFoodEventPost = post;
            }

            @Override
            public ScheduledFuture<?> getPost() {
                return scheduledFoodEventPost;
            }
        });
    }

    public List<Food> getFoodData() {
        try {
            return this.getDao().queryForAll();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }

        return new ArrayList<>();
    }

    /*
    {
        "_id": "551ee3ad368e06e80856e6a9",
        "type": "food",
        "category": "Zakladni",
        "subcategory": "Napoje",
        "name": "Mleko",
        "portion": 250,
        "carbs": 12,
        "gi": 1,
        "created_at": "2015-04-14T06:59:16.500Z",
        "unit": "ml"
    }
     */
    public void createFoodFromJsonIfNotExists(JSONObject json) {
        try {
            Food food = Food.createFromJson(json);
            this.createFoodFromJsonIfNotExists(food);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void createFoodFromJsonIfNotExists(JSONArray array) {
        try {
            for (int n = 0; n < array.length(); n++) {
                JSONObject json = array.getJSONObject(n);
                Food food = Food.createFromJson(json);
                this.createFoodFromJsonIfNotExists(food);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void createFoodFromJsonIfNotExists(Food food) {
        this.createOrUpdateByNS(food);
    }

    public void deleteNS(JSONObject json) {
        try {
            String _id = json.getString("_id");
            this.deleteByNSId(_id);
        } catch (JSONException | SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void deleteNS(JSONArray array) {
        try {
            for (int n = 0; n < array.length(); n++) {
                JSONObject json = array.getJSONObject(n);
                this.deleteNS(json);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    /**
     * deletes an entry by its NS Id.
     * <p>
     * Basically a convenience method for findByNSId and delete.
     *
     * @param _id
     */
    public void deleteByNSId(String _id) throws SQLException {
        Food stored = this.findByNSId(_id);
        if (stored != null) {
            if (L.isEnabled(L.DATAFOOD))
                log.debug("Removing Food record from database: " + stored.toString());
            this.delete(stored);
        }
    }

    /**
     * deletes the food and sends the foodChange Event
     * <p>
     * should be moved ot a Service
     *
     * @param food
     */
    public void delete(Food food) {
        try {
            this.getDao().delete(food);
            this.scheduleFoodChange();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    /**
     * Create of update a food record by the NS (Nightscout) Id.
     *
     * @param food
     * @return
     */
    public boolean createOrUpdateByNS(Food food) {
        // find by NS _id
        if (food._id != null && !food._id.equals("")) {
            Food old = this.findByNSId(food._id);

            if (old != null) {
                if (!old.isEqual(food)) {
                    this.delete(old); // need to delete/create because date may change too
                    old.copyFrom(food);
                    this.create(old);
                    return true;
                } else {
                    return false;
                }
            } else {
                this.createOrUpdate(food);
                return true;
            }
        }

        return false;
    }

    public void createOrUpdate(Food food) {
        try {
            this.getDao().createOrUpdate(food);
            if (L.isEnabled(L.DATAFOOD))
                log.debug("Created or Updated: " + food.toString());
        } catch (SQLException e) {
            log.error("Unable to createOrUpdate Food", e);
        }
        this.scheduleFoodChange();
    }

    public void create(Food food) {
        try {
            this.getDao().create(food);
            if (L.isEnabled(L.DATAFOOD))
                log.debug("New record: " + food.toString());
        } catch (SQLException e) {
            log.error("Unable to create Food", e);
        }
        this.scheduleFoodChange();
    }

    /**
     * finds food by its NS Id.
     *
     * @param _id
     * @return
     */
    @Nullable
    public Food findByNSId(String _id) {
        try {
            List<Food> list = this.getDao().queryForEq("_id", _id);

            if (list.size() == 1) { // really? if there are more then one result, then we do not return anything...
                return list.get(0);
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
