/**
 * @file ble_passport_service.c
 * @brief BLE Service Implementation for Passport Data Transfer
 */

#include "ble_passport_service.h"
#include <zephyr/logging/log.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/uuid.h>
#include <zephyr/bluetooth/gatt.h>

LOG_MODULE_REGISTER(ble_passport_svc, LOG_LEVEL_DBG);

/* ==================== Global Variables ==================== */
static struct bt_conn *current_conn = NULL;
static void (*command_callback)(passport_command_t cmd) = NULL;
static passport_status_t current_status = PASSPORT_STATUS_IDLE;
static passport_data_t current_data = {0};

/* ==================== GATT Characteristics ==================== */

/* Status Characteristic - Notify */
static void status_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value)
{
    LOG_INF("Status notifications %s", value == BT_GATT_CCC_NOTIFY ? "enabled" : "disabled");
}

/* Data Characteristic - Notify */
static void data_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value)
{
    LOG_INF("Data notifications %s", value == BT_GATT_CCC_NOTIFY ? "enabled" : "disabled");
}

/* Control Characteristic - Write */
static ssize_t control_write(struct bt_conn *conn,
                             const struct bt_gatt_attr *attr,
                             const void *buf, uint16_t len, uint16_t offset,
                             uint8_t flags)
{
    const uint8_t *data = buf;

    if (len != 1)
    {
        LOG_WRN("Invalid command length: %d", len);
        return BT_GATT_ERR(BT_ATT_ERR_INVALID_ATTRIBUTE_LEN);
    }

    passport_command_t cmd = (passport_command_t)data[0];
    LOG_INF("Control write: command=0x%02X", cmd);

    if (command_callback)
    {
        command_callback(cmd);
    }

    return len;
}

/* GATT Service Definition */
BT_GATT_SERVICE_DEFINE(passport_svc,
                       BT_GATT_PRIMARY_SERVICE(BT_UUID_PASSPORT_SERVICE),

                       /* Status Characteristic (Read + Notify) */
                       BT_GATT_CHARACTERISTIC(BT_UUID_PASSPORT_STATUS,
                                              BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY,
                                              BT_GATT_PERM_READ,
                                              NULL, NULL, &current_status),
                       BT_GATT_CCC(status_ccc_cfg_changed,
                                   BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),

                       /* Data Characteristic (Read + Notify) */
                       BT_GATT_CHARACTERISTIC(BT_UUID_PASSPORT_DATA,
                                              BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY,
                                              BT_GATT_PERM_READ,
                                              NULL, NULL, &current_data),
                       BT_GATT_CCC(data_ccc_cfg_changed,
                                   BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),

                       /* Control Characteristic (Write) */
                       BT_GATT_CHARACTERISTIC(BT_UUID_PASSPORT_CONTROL,
                                              BT_GATT_CHRC_WRITE,
                                              BT_GATT_PERM_WRITE,
                                              NULL, control_write, NULL), );

/* ==================== Connection Callbacks ==================== */

static void connected(struct bt_conn *conn, uint8_t err)
{
    if (err)
    {
        LOG_ERR("Connection failed (err %u)", err);
        return;
    }

    current_conn = bt_conn_ref(conn);

    char addr[BT_ADDR_LE_STR_LEN];
    bt_addr_le_to_str(bt_conn_get_dst(conn), addr, sizeof(addr));
    LOG_INF("Connected: %s", addr);
}

static void disconnected(struct bt_conn *conn, uint8_t reason)
{
    char addr[BT_ADDR_LE_STR_LEN];
    bt_addr_le_to_str(bt_conn_get_dst(conn), addr, sizeof(addr));
    LOG_INF("Disconnected: %s (reason %u)", addr, reason);

    if (current_conn)
    {
        bt_conn_unref(current_conn);
        current_conn = NULL;
    }
}

BT_CONN_CB_DEFINE(conn_callbacks) = {
    .connected = connected,
    .disconnected = disconnected,
};

/* ==================== Advertising ==================== */

/* Simple advertising without UUID (still discoverable by name) */
static const struct bt_data ad[] = {
    BT_DATA_BYTES(BT_DATA_FLAGS, (BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR)),
};

/* Scan response data with device name */
static const struct bt_data sd[] = {
    BT_DATA(BT_DATA_NAME_COMPLETE, CONFIG_BT_DEVICE_NAME, sizeof(CONFIG_BT_DEVICE_NAME) - 1)};

/* ==================== Advertising ==================== */

static int start_advertising(void)
{
    int err;

    LOG_INF("Starting advertising...");
    LOG_INF("Device name: %s", CONFIG_BT_DEVICE_NAME);

    /* Log advertising data for debugging */
    for (int i = 0; i < ARRAY_SIZE(ad); i++)
    {
        LOG_INF("AD[%d] type=0x%02x len=%d", i, ad[i].type, ad[i].data_len);
    }

    /* Use BT_LE_ADV_CONN_NAME which automatically includes the device name */
    err = bt_le_adv_start(BT_LE_ADV_CONN_NAME, ad, ARRAY_SIZE(ad), NULL, 0);
    if (err)
    {
        LOG_ERR("Advertising failed to start (err %d)", err);
        return err;
    }

    LOG_INF("✓✓✓ Advertising started ✓✓✓");
    LOG_INF("Waiting for Android connection...");
    LOG_INF("Android can discover by name: %s", CONFIG_BT_DEVICE_NAME);
    LOG_INF("Service UUID will be discovered after connection");

    return 0;
}

/* ==================== Public API ==================== */

int ble_passport_service_init(void)
{
    int err;

    LOG_INF("BLE init");

    err = bt_enable(NULL);
    if (err)
    {
        LOG_ERR("BT failed: %d", err);
        return err;
    }

    LOG_INF("BT ready");

    /* Start advertising */
    err = start_advertising();
    if (err)
    {
        LOG_ERR("Failed to start advertising");
        return err;
    }

    return 0;
}

int ble_passport_send_status(passport_status_t status)
{
    LOG_DBG("Status: 0x%02X", status);

    current_status = status;

    if (current_conn)
    {
        int err = bt_gatt_notify(current_conn, &passport_svc.attrs[2],
                                 &current_status, sizeof(current_status));
        if (err)
        {
            LOG_WRN("Notify failed: %d", err);
            return err;
        }
        LOG_DBG("Status notification sent");
    }

    return 0;
}

int ble_passport_send_data(const passport_data_t *data)
{
    LOG_INF("Send data");

    if (!data)
    {
        return -EINVAL;
    }

    memcpy(&current_data, data, sizeof(passport_data_t));

    if (current_conn)
    {
        int err = bt_gatt_notify(current_conn, &passport_svc.attrs[5],
                                 &current_data, sizeof(current_data));
        if (err)
        {
            LOG_WRN("Notify failed: %d", err);
            return err;
        }
        LOG_INF("Data notification sent");
    }

    return 0;
}

void ble_passport_set_data_callback(void (*callback)(passport_command_t cmd))
{
    command_callback = callback;
    LOG_INF("Callback set");
}