/**
 * @file main.c
 * @brief NFC Passport Reader with BLE - Android Integration
 */

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/devicetree.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/drivers/i2c.h>
#include <zephyr/logging/log.h>
#include <string.h>

#include "ble_passport_service.h"

LOG_MODULE_REGISTER(nfc_passport, LOG_LEVEL_DBG);

/* ==================== PN532 Configuration ==================== */
static uint8_t pn532_i2c_address = 0x24; // Will auto-detect 0x24 or 0x48
#define PN532_PREAMBLE 0x00
#define PN532_STARTCODE1 0x00
#define PN532_STARTCODE2 0xFF
#define PN532_POSTAMBLE 0x00

#define PN532_HOSTTOPN532 0xD4
#define PN532_PN532TOHOST 0xD5

/* PN532 Commands */
#define PN532_CMD_GETFIRMWAREVERSION 0x02
#define PN532_CMD_SAMCONFIGURATION 0x14
#define PN532_CMD_INLISTPASSIVETARGET 0x4A
#define PN532_CMD_INDATAEXCHANGE 0x40

/* ISO14443A Types */
#define PN532_MIFARE_ISO14443A 0x00

/* GPIO Pins */
#define PN532_IRQ_NODE DT_ALIAS(pn532irq)
#define PN532_RST_NODE DT_ALIAS(pn532rst)

#define LED0_NODE DT_ALIAS(led0)
#define LED1_NODE DT_ALIAS(led1)
#define LED2_NODE DT_ALIAS(led2)
#define LED3_NODE DT_ALIAS(led3)

/* ==================== Type Definitions ==================== */
#define APDU_MAX_LEN 261

typedef struct
{
        uint8_t data[APDU_MAX_LEN];
        uint16_t len;
} apdu_t;

typedef enum
{
        STATE_IDLE,
        STATE_INIT_PN532,
        STATE_WAIT_COMMAND,
        STATE_DETECTING,
        STATE_CARD_DETECTED,
        STATE_SELECTING_APP,
        STATE_READING_DG1,
        STATE_SUCCESS,
        STATE_ERROR
} passport_state_t;

typedef struct
{
        passport_state_t state;
        uint8_t uid[10];
        uint8_t uid_len;
        uint8_t target_number;
        bool card_present;
        bool scan_requested;
        passport_data_t passport_data;
} passport_reader_t;

/* ==================== Global Variables ==================== */
static const struct device *i2c_dev;
static const struct gpio_dt_spec pn532_irq = GPIO_DT_SPEC_GET(PN532_IRQ_NODE, gpios);
static const struct gpio_dt_spec pn532_rst = GPIO_DT_SPEC_GET(PN532_RST_NODE, gpios);

static const struct gpio_dt_spec led0 = GPIO_DT_SPEC_GET(LED0_NODE, gpios);
static const struct gpio_dt_spec led1 = GPIO_DT_SPEC_GET(LED1_NODE, gpios);
static const struct gpio_dt_spec led2 = GPIO_DT_SPEC_GET(LED2_NODE, gpios);
static const struct gpio_dt_spec led3 = GPIO_DT_SPEC_GET(LED3_NODE, gpios);

static passport_reader_t reader = {0};

/* ==================== PN532 Functions - Arduino Style ==================== */

static int pn532_wakeup(void)
{
        LOG_DBG("Waking up PN532...");

        // Send wakeup sequence (like Arduino library)
        uint8_t wake_cmd[] = {0x55, 0x55, 0x00, 0x00, 0x00};
        int ret = i2c_write(i2c_dev, wake_cmd, sizeof(wake_cmd), pn532_i2c_address);

        if (ret != 0)
        {
                LOG_WRN("Wakeup write failed: %d", ret);
        }

        k_sleep(K_MSEC(20));

        return 0;
}

static int pn532_read_ack(void)
{
        uint8_t ack[7];

        int ret = i2c_read(i2c_dev, ack, sizeof(ack), pn532_i2c_address);
        if (ret != 0)
        {
                return ret;
        }

        // Log ACK frame for debugging
        LOG_HEXDUMP_DBG(ack, sizeof(ack), "ACK frame:");

        // Skip ready byte if present
        uint8_t offset = (ack[0] == 0x01) ? 1 : 0;

        if (offset == 1)
        {
                LOG_DBG("ACK has I2C ready byte (0x01)");
        }

        // ACK is optional in I2C mode
        LOG_DBG("ACK skipped (optional in I2C mode)");
        return 0;
}

static int pn532_write_command(const uint8_t *cmd, uint8_t cmd_len)
{
        uint8_t frame[64];
        uint8_t idx = 0;

        frame[idx++] = PN532_PREAMBLE;
        frame[idx++] = PN532_STARTCODE1;
        frame[idx++] = PN532_STARTCODE2;
        frame[idx++] = cmd_len + 1;
        frame[idx++] = ~(cmd_len + 1) + 1;
        frame[idx++] = PN532_HOSTTOPN532;

        memcpy(&frame[idx], cmd, cmd_len);
        idx += cmd_len;

        uint8_t dcs = PN532_HOSTTOPN532;
        for (uint8_t i = 0; i < cmd_len; i++)
        {
                dcs += cmd[i];
        }
        frame[idx++] = ~dcs + 1;
        frame[idx++] = PN532_POSTAMBLE;

        LOG_HEXDUMP_DBG(frame, idx, "TX frame:");

        return i2c_write(i2c_dev, frame, idx, pn532_i2c_address);
}

static int pn532_read_response(uint8_t *resp, uint8_t *resp_len, uint16_t timeout_ms)
{
        uint8_t frame[64];
        int ret;

        // Wait for response ready
        k_sleep(K_MSEC(50));

        // Read ACK first (Arduino style)
        ret = pn532_read_ack();
        if (ret != 0)
        {
                LOG_DBG("ACK read failed or invalid");
        }

        // Small delay between ACK and response
        k_sleep(K_MSEC(20));

        // Read response frame
        ret = i2c_read(i2c_dev, frame, sizeof(frame), pn532_i2c_address);
        if (ret != 0)
        {
                LOG_ERR("Failed to read response frame: %d", ret);
                return ret;
        }

        // Log raw frame for debugging
        LOG_HEXDUMP_DBG(frame, 16, "RX frame:");

        // Check for "not ready" pattern (0x00 0x80 0x80...)
        if (frame[0] == 0x00 && frame[1] == 0x80)
        {
                LOG_DBG("PN532 not ready or no card present");
                return -EAGAIN; // Try again
        }

        // Skip the I2C ready status byte (0x01) if present
        uint8_t offset = 0;
        if (frame[0] == 0x01)
        {
                offset = 1;
                LOG_DBG("Skipped I2C ready byte");
        }

        // Validate frame header (after offset)
        if (frame[offset] != PN532_PREAMBLE ||
            frame[offset + 1] != PN532_STARTCODE1 ||
            frame[offset + 2] != PN532_STARTCODE2)
        {
                LOG_ERR("Invalid response frame header");
                LOG_ERR("Expected: 00 00 FF, Got: %02X %02X %02X",
                        frame[offset], frame[offset + 1], frame[offset + 2]);
                LOG_HEXDUMP_ERR(frame, 16, "Full frame:");
                return -EINVAL;
        }

        uint8_t len = frame[offset + 3];
        uint8_t lcs = frame[offset + 4];

        // Validate length checksum
        if ((uint8_t)(len + lcs) != 0)
        {
                LOG_WRN("Length checksum mismatch: LEN=0x%02X, LCS=0x%02X", len, lcs);
        }

        LOG_DBG("Frame header OK - Preamble: %02X %02X %02X, LEN: %02X, LCS: %02X",
                frame[offset], frame[offset + 1], frame[offset + 2], len, lcs);

        *resp_len = len - 2; // Subtract TFI and checksum
        memcpy(resp, &frame[offset + 6], *resp_len);

        LOG_HEXDUMP_DBG(resp, *resp_len, "Response data:");

        return 0;
}

static int pn532_reset(void)
{
        LOG_INF("Resetting PN532...");
        gpio_pin_set_dt(&pn532_rst, 0);
        k_sleep(K_MSEC(100));
        gpio_pin_set_dt(&pn532_rst, 1);
        k_sleep(K_MSEC(500));
        return 0;
}

static int pn532_init(void)
{
        int ret;
        uint8_t cmd[16];
        uint8_t resp[32];
        uint8_t resp_len;

        LOG_INF("Initializing PN532 (Arduino style)...");

        // Hardware reset
        ret = pn532_reset();
        if (ret != 0)
        {
                LOG_ERR("Reset failed");
                return ret;
        }

        // Try both addresses with retries (like Arduino library)
        uint8_t addresses[] = {0x24, 0x48};
        bool found = false;

        for (int addr_idx = 0; addr_idx < 2 && !found; addr_idx++)
        {
                pn532_i2c_address = addresses[addr_idx];
                LOG_INF("Trying PN532 at address 0x%02X...", pn532_i2c_address);

                // Wakeup sequence
                pn532_wakeup();

                // Try to get firmware version with retries
                for (int retry = 0; retry < 3; retry++)
                {
                        LOG_INF("  Attempt %d/3", retry + 1);

                        cmd[0] = PN532_CMD_GETFIRMWAREVERSION;
                        ret = pn532_write_command(cmd, 1);
                        if (ret != 0)
                        {
                                LOG_WRN("  Write failed: %d", ret);
                                k_sleep(K_MSEC(100));
                                continue;
                        }

                        ret = pn532_read_response(resp, &resp_len, 1000);
                        if (ret == 0 && resp[0] == (PN532_CMD_GETFIRMWAREVERSION + 1))
                        {
                                LOG_INF("✓✓✓ PN532 FOUND at 0x%02X! ✓✓✓", pn532_i2c_address);
                                LOG_INF("Firmware: v%d.%d", resp[1], resp[2]);
                                found = true;
                                break;
                        }
                        else
                        {
                                LOG_WRN("  Read failed: %d", ret);
                                k_sleep(K_MSEC(200));
                        }
                }

                if (found)
                        break;
        }

        if (!found)
        {
                LOG_ERR("PN532 not found at 0x24 or 0x48");
                LOG_ERR("Check:");
                LOG_ERR("  1. PN532 power (VCC = 3.3V)");
                LOG_ERR("  2. PN532 mode switches (I2C mode: SEL0=OFF, SEL1=ON)");
                LOG_ERR("  3. Wiring (SDA, SCL connections)");
                return -ENODEV;
        }

        // Configure SAM (like Arduino)
        LOG_INF("Configuring SAM...");
        cmd[0] = PN532_CMD_SAMCONFIGURATION;
        cmd[1] = 0x01; // Normal mode
        cmd[2] = 0x14; // Timeout 50ms * 20 = 1 second
        cmd[3] = 0x01; // Use IRQ pin

        ret = pn532_write_command(cmd, 4);
        if (ret != 0)
        {
                LOG_ERR("SAM config write failed");
                return ret;
        }

        ret = pn532_read_response(resp, &resp_len, 1000);
        if (ret != 0)
        {
                LOG_ERR("SAM config response failed");
                return ret;
        }

        LOG_INF("✓ PN532 initialized successfully!");
        return 0;
}

static int pn532_detect_card(void)
{
        uint8_t cmd[16];
        uint8_t resp[32];
        uint8_t resp_len;
        int ret;

        cmd[0] = PN532_CMD_INLISTPASSIVETARGET;
        cmd[1] = 0x01;
        cmd[2] = PN532_MIFARE_ISO14443A;

        ret = pn532_write_command(cmd, 3);
        if (ret != 0)
                return ret;

        ret = pn532_read_response(resp, &resp_len, 2000);
        if (ret != 0)
                return ret;

        if (resp[0] != (PN532_CMD_INLISTPASSIVETARGET + 1))
        {
                return -EINVAL;
        }

        uint8_t num_targets = resp[1];
        if (num_targets == 0)
        {
                return -ENODEV;
        }

        reader.target_number = resp[2];
        reader.uid_len = resp[6];
        memcpy(reader.uid, &resp[7], reader.uid_len);

        LOG_INF("Card detected!");
        LOG_HEXDUMP_INF(reader.uid, reader.uid_len, "UID:");

        /* Store UID in passport data */
        memcpy(reader.passport_data.uid, reader.uid, reader.uid_len);
        reader.passport_data.uid_len = reader.uid_len;

        reader.card_present = true;
        return 0;
}

/* Select ePassport Application */
static const uint8_t SELECT_EPASSPORT_APP[] = {
    0x00, 0xA4, 0x04, 0x0C, 0x07, 0xA0, 0x00, 0x00, 0x02, 0x47, 0x10, 0x01};

static int select_passport_application(void)
{
        uint8_t cmd[256];
        uint8_t resp[256];
        uint8_t resp_len;
        int ret;

        cmd[0] = PN532_CMD_INDATAEXCHANGE;
        cmd[1] = reader.target_number;
        memcpy(&cmd[2], SELECT_EPASSPORT_APP, sizeof(SELECT_EPASSPORT_APP));

        ret = pn532_write_command(cmd, sizeof(SELECT_EPASSPORT_APP) + 2);
        if (ret != 0)
                return ret;

        ret = pn532_read_response(resp, &resp_len, 3000);
        if (ret != 0)
                return ret;

        /* Check for success (SW1=0x90, SW2=0x00) */
        if (resp_len >= 4 && resp[resp_len - 2] == 0x90 && resp[resp_len - 1] == 0x00)
        {
                LOG_INF("ePassport application selected");
                return 0;
        }

        LOG_ERR("SELECT failed");
        return -EIO;
}

static int read_passport_mrz(void)
{
        /* Mock data for demonstration */
        /* In production, this would read DG1 and parse MRZ */

        strncpy(reader.passport_data.document_number, "A12345678", 10);
        strncpy(reader.passport_data.surname, "DOE", 40);
        strncpy(reader.passport_data.given_names, "JOHN", 40);
        strncpy(reader.passport_data.nationality, "USA", 4);
        strncpy(reader.passport_data.date_of_birth, "19900101", 9);
        strncpy(reader.passport_data.sex, "M", 2);
        strncpy(reader.passport_data.expiry_date, "20301231", 9);
        reader.passport_data.photo_available = 0;

        LOG_INF("Passport MRZ read (mock data)");
        LOG_INF("  Doc: %s", reader.passport_data.document_number);
        LOG_INF("  Name: %s, %s", reader.passport_data.surname,
                reader.passport_data.given_names);

        return 0;
}

/* ==================== BLE Command Handler ==================== */

static void handle_ble_command(passport_command_t cmd)
{
        LOG_INF("BLE Command received: 0x%02X", cmd);

        switch (cmd)
        {
        case PASSPORT_CMD_START_SCAN:
                LOG_INF("Start scan requested");
                reader.scan_requested = true;
                ble_passport_send_status(PASSPORT_STATUS_SCANNING);
                gpio_pin_set_dt(&led0, 1);
                break;

        case PASSPORT_CMD_STOP_SCAN:
                LOG_INF("Stop scan requested");
                reader.scan_requested = false;
                reader.state = STATE_WAIT_COMMAND;
                ble_passport_send_status(PASSPORT_STATUS_IDLE);
                gpio_pin_set_dt(&led0, 0);
                break;

        case PASSPORT_CMD_GET_DATA:
                LOG_INF("Get data requested");
                if (reader.card_present)
                {
                        ble_passport_send_data(&reader.passport_data);
                }
                break;

        case PASSPORT_CMD_RESET:
                LOG_INF("Reset requested");
                memset(&reader, 0, sizeof(reader));
                reader.state = STATE_WAIT_COMMAND;
                ble_passport_send_status(PASSPORT_STATUS_IDLE);
                break;

        default:
                LOG_WRN("Unknown command: 0x%02X", cmd);
                break;
        }
}

/* ==================== State Machine ==================== */

static void passport_state_machine(void)
{
        int ret;

        switch (reader.state)
        {
        case STATE_IDLE:
                LOG_INF("State: IDLE");
                reader.state = STATE_INIT_PN532;
                break;

        case STATE_INIT_PN532:
                LOG_INF("State: INIT_PN532");
                gpio_pin_set_dt(&led0, 1);

                ret = pn532_init();
                if (ret == 0)
                {
                        reader.state = STATE_WAIT_COMMAND;
                        ble_passport_send_status(PASSPORT_STATUS_IDLE);
                        gpio_pin_set_dt(&led0, 0);
                }
                else
                {
                        LOG_ERR("PN532 init failed");
                        reader.state = STATE_ERROR;
                        ble_passport_send_status(PASSPORT_STATUS_ERROR);
                }
                break;

        case STATE_WAIT_COMMAND:
                /* Wait for BLE command to start scanning */
                if (reader.scan_requested)
                {
                        reader.state = STATE_DETECTING;
                }
                k_sleep(K_MSEC(100));
                break;

        case STATE_DETECTING:
                if (!reader.scan_requested)
                {
                        reader.state = STATE_WAIT_COMMAND;
                        break;
                }

                ble_passport_send_status(PASSPORT_STATUS_SCANNING);
                ret = pn532_detect_card();
                if (ret == 0)
                {
                        reader.state = STATE_CARD_DETECTED;
                        gpio_pin_set_dt(&led1, 1);
                }
                else
                {
                        k_sleep(K_MSEC(500));
                }
                break;

        case STATE_CARD_DETECTED:
                LOG_INF("State: CARD_DETECTED");
                gpio_pin_set_dt(&led2, 1);
                ble_passport_send_status(PASSPORT_STATUS_READING);
                reader.state = STATE_SELECTING_APP;
                break;

        case STATE_SELECTING_APP:
                LOG_INF("State: SELECTING_APP");

                ret = select_passport_application();
                if (ret == 0)
                {
                        reader.state = STATE_READING_DG1;
                }
                else
                {
                        reader.state = STATE_ERROR;
                        ble_passport_send_status(PASSPORT_STATUS_ERROR);
                }
                break;

        case STATE_READING_DG1:
                LOG_INF("State: READING_DG1");

                ret = read_passport_mrz();
                if (ret == 0)
                {
                        reader.state = STATE_SUCCESS;
                }
                else
                {
                        reader.state = STATE_ERROR;
                }
                break;

        case STATE_SUCCESS:
                LOG_INF("State: SUCCESS");
                gpio_pin_set_dt(&led2, 1);
                gpio_pin_set_dt(&led3, 0);

                LOG_INF("=== Passport Read Complete ===");

                /* Send success status and data via BLE */
                ble_passport_send_status(PASSPORT_STATUS_SUCCESS);
                k_sleep(K_MSEC(100));
                ble_passport_send_data(&reader.passport_data);

                k_sleep(K_SECONDS(2));

                /* Reset for next scan */
                reader.card_present = false;
                reader.state = STATE_WAIT_COMMAND;
                gpio_pin_set_dt(&led1, 0);
                gpio_pin_set_dt(&led2, 0);
                break;

        case STATE_ERROR:
                LOG_ERR("State: ERROR");
                gpio_pin_set_dt(&led3, 1);
                ble_passport_send_status(PASSPORT_STATUS_ERROR);

                k_sleep(K_SECONDS(2));

                memset(&reader, 0, sizeof(reader));
                reader.state = STATE_WAIT_COMMAND;
                gpio_pin_set_dt(&led0, 0);
                gpio_pin_set_dt(&led1, 0);
                gpio_pin_set_dt(&led2, 0);
                gpio_pin_set_dt(&led3, 0);
                break;
        }
}

/* ==================== Main ==================== */

static void i2c_scan_detailed(void)
{
        LOG_INF("=== Detailed I2C Bus Scan ===");
        bool found = false;
        int success_count = 0;
        int nack_count = 0;
        int other_error_count = 0;

        for (uint8_t addr = 0x03; addr < 0x78; addr++)
        {
                uint8_t dummy;
                int ret = i2c_read(i2c_dev, &dummy, 1, addr);

                if (ret == 0)
                {
                        LOG_INF("  ✓ Device found at: 0x%02X", addr);
                        found = true;
                        success_count++;
                }
                else if (ret == -EIO || ret == -ENXIO)
                {
                        nack_count++;
                }
                else
                {
                        LOG_WRN("  ? Address 0x%02X returned error: %d", addr, ret);
                        other_error_count++;
                }
        }

        LOG_INF("=== Scan Results ===");
        LOG_INF("  Devices found: %d", success_count);
        LOG_INF("  NACKs (normal): %d", nack_count);
        LOG_INF("  Other errors: %d", other_error_count);

        if (!found)
        {
                LOG_WRN("No I2C devices found in scan");
                LOG_WRN("PN532 will be detected during init with retries");
        }
        LOG_INF("=========================");
}

int main(void)
{
        int ret;

        LOG_INF("=== NFC Passport Reader with BLE ===");
        LOG_INF("Build: " __DATE__ " " __TIME__);

        /* Get I2C device */
        i2c_dev = DEVICE_DT_GET(DT_NODELABEL(i2c0));

        if (!device_is_ready(i2c_dev))
        {
                LOG_ERR("I2C device not ready");
                return -ENODEV;
        }

        LOG_INF("I2C device ready");
        i2c_scan_detailed();

        /* Initialize GPIOs */
        if (!gpio_is_ready_dt(&pn532_irq))
        {
                LOG_ERR("PN532 IRQ GPIO not ready");
                return -ENODEV;
        }
        if (!gpio_is_ready_dt(&pn532_rst))
        {
                LOG_ERR("PN532 RST GPIO not ready");
                return -ENODEV;
        }

        gpio_pin_configure_dt(&pn532_irq, GPIO_INPUT);
        gpio_pin_configure_dt(&pn532_rst, GPIO_OUTPUT_ACTIVE);

        gpio_pin_configure_dt(&led0, GPIO_OUTPUT_INACTIVE);
        gpio_pin_configure_dt(&led1, GPIO_OUTPUT_INACTIVE);
        gpio_pin_configure_dt(&led2, GPIO_OUTPUT_INACTIVE);
        gpio_pin_configure_dt(&led3, GPIO_OUTPUT_INACTIVE);

        LOG_INF("Hardware initialized");

        /* Initialize BLE */
        ret = ble_passport_service_init();
        if (ret)
        {
                LOG_ERR("BLE init failed (err %d)", ret);
                return ret;
        }

        /* Register BLE command callback */
        ble_passport_set_data_callback(handle_ble_command);

        LOG_INF("BLE Passport Reader ready");
        LOG_INF("Connect via Android app and send START_SCAN command");

        reader.state = STATE_IDLE;

        while (1)
        {
                passport_state_machine();
                k_sleep(K_MSEC(100));
        }

        return 0;
}