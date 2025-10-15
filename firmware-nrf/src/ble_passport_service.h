/**
 * @file ble_passport_service.h
 * @brief BLE Service for Passport Data Transfer
 */

#ifndef BLE_PASSPORT_SERVICE_H_
#define BLE_PASSPORT_SERVICE_H_

#include <zephyr/kernel.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/uuid.h>
#include <zephyr/bluetooth/gatt.h>

/* Service UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E */
#define BT_UUID_PASSPORT_SERVICE_VAL \
    BT_UUID_128_ENCODE(0x6e400001, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e)

/* Status Characteristic UUID: 6E400002-B5A3-F393-E0A9-E50E24DCCA9E */
#define BT_UUID_PASSPORT_STATUS_VAL \
    BT_UUID_128_ENCODE(0x6e400002, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e)

/* Data Characteristic UUID: 6E400003-B5A3-F393-E0A9-E50E24DCCA9E */
#define BT_UUID_PASSPORT_DATA_VAL \
    BT_UUID_128_ENCODE(0x6e400003, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e)

/* Control Characteristic UUID: 6E400004-B5A3-F393-E0A9-E50E24DCCA9E */
#define BT_UUID_PASSPORT_CONTROL_VAL \
    BT_UUID_128_ENCODE(0x6e400004, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e)

#define BT_UUID_PASSPORT_SERVICE BT_UUID_DECLARE_128(BT_UUID_PASSPORT_SERVICE_VAL)
#define BT_UUID_PASSPORT_STATUS BT_UUID_DECLARE_128(BT_UUID_PASSPORT_STATUS_VAL)
#define BT_UUID_PASSPORT_DATA BT_UUID_DECLARE_128(BT_UUID_PASSPORT_DATA_VAL)
#define BT_UUID_PASSPORT_CONTROL BT_UUID_DECLARE_128(BT_UUID_PASSPORT_CONTROL_VAL)

/* Status Values */
typedef enum
{
    PASSPORT_STATUS_IDLE = 0x00,
    PASSPORT_STATUS_SCANNING = 0x01,
    PASSPORT_STATUS_READING = 0x02,
    PASSPORT_STATUS_SUCCESS = 0x03,
    PASSPORT_STATUS_ERROR = 0x04,
    PASSPORT_STATUS_NO_CARD = 0x05
} passport_status_t;

/* Control Commands */
typedef enum
{
    PASSPORT_CMD_START_SCAN = 0x01,
    PASSPORT_CMD_STOP_SCAN = 0x02,
    PASSPORT_CMD_GET_DATA = 0x03,
    PASSPORT_CMD_RESET = 0x04
} passport_command_t;

/* Passport Data Structure */
typedef struct
{
    char document_number[10];
    char surname[40];
    char given_names[40];
    char nationality[4];
    char date_of_birth[9];
    char sex[2];
    char expiry_date[9];
    uint8_t uid[10];
    uint8_t uid_len;
    uint8_t photo_available;
} passport_data_t;

/* Function declarations */
int ble_passport_service_init(void);
int ble_passport_send_status(passport_status_t status);
int ble_passport_send_data(const passport_data_t *data);
void ble_passport_set_data_callback(void (*callback)(passport_command_t cmd));

#endif /* BLE_PASSPORT_SERVICE_H_ */
