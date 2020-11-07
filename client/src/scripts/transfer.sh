CLIENT1_NAME=$1

SCRIPT_DIR="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
source $SCRIPT_DIR/client-api.sh

CLIENT1_ID=$(create_client $CLIENT1_NAME)
ACCOUNT1_1=$(create_debit_account $CLIENT1_ID 1000)

CLIENT2_ID=$(create_client $CLIENT2_NAME)
ACCOUNT2_1=$(create_debit_account $CLIENT2_ID 2000)

print_client $CLIENT1_ID
print_client $CLIENT2_ID

echo
echo Order payment
echo
order_payment $CLIENT1_ID $ACCOUNT1_1 $ACCOUNT2_1 800 | jq -r '.transaction'

sleep 2

print_client $CLIENT1_ID
print_client $CLIENT2_ID
