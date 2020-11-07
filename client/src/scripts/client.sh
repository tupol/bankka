CLIENT_NAME=$1

SCRIPT_DIR="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

source $SCRIPT_DIR/client-api.sh

CLIENT_ID=$(create_client $CLIENT_NAME)

print_client $CLIENT_ID

 echo
 echo Deactivating client $CLIENT_NAME with id $CLIENT_ID
 echo

deactivate_client $CLIENT_ID
print_client $CLIENT_ID

echo
echo Activating client $CLIENT_NAME with id $CLIENT_ID
echo

activate_client $CLIENT_ID
print_client $CLIENT_ID

echo
echo Creating credit account for client $CLIENT_NAME with id $CLIENT_ID
echo
ACCOUNT1=$(create_credit_account $CLIENT_ID 1000)

print_client $CLIENT_ID

echo
echo Creating debit account for client $CLIENT_NAME with id $CLIENT_ID
echo
ACCOUNT2=$(create_debit_account $CLIENT_ID 1000)

print_client $CLIENT_ID



echo
echo Order payment
echo
order_payment $CLIENT_ID $ACCOUNT2 $ACCOUNT1 100 | jq -r '.transaction'

print_client $CLIENT_ID



echo
echo Order payment
echo
order_payment $CLIENT_ID $ACCOUNT2 $ACCOUNT1 1000

print_client $CLIENT_ID

