
CLIENT_NAME=$1


echo Creating client $CLIENT_NAME

curl -s -X POST http://127.0.0.1:12552/create -F "clientName=$CLIENT_NAME"

echo

CLIENT_ID=$(curl -s -X POST http://127.0.0.1:12552/create -F "clientName=$CLIENT_NAME" | jq -r '.client.id.value')

echo
echo Created client $CLIENT_NAME with id $CLIENT_ID
echo

curl -s -X GET  http://127.0.0.1:12551/find -F "clientId=$CLIENT_ID"

echo
echo Deactivating client $CLIENT_NAME with id $CLIENT_ID
echo

curl -s -X POST  http://127.0.0.1:12551/deactivate -F "clientId=$CLIENT_ID"

echo
echo Activating client $CLIENT_NAME with id $CLIENT_ID
echo
echo 'curl -s -X POST  http://127.0.0.1:12551/activate -F "clientId='$CLIENT_ID'"'
curl -s -X POST  http://127.0.0.1:12551/activate -F "clientId=$CLIENT_ID"

echo
echo Creating credit account for client $CLIENT_NAME with id $CLIENT_ID
echo
echo "curl -s -X POST http://127.0.0.1:12551/create-account  -F "clientId=$CLIENT_ID" -F "creditLimit=1000" -F "initialAmount=0""
RESPONSE=$(curl -s -X POST http://127.0.0.1:12551/create-account?clientId -F "clientId=$CLIENT_ID" -F "creditLimit=1000" -F "initialAmount=0")
ACCOUNT1=$(echo $RESPONSE | jq -r '.accountId.value')

echo
echo "Created account $ACCOUNT1"

echo
echo Creating debit account for client $CLIENT_NAME with id $CLIENT_ID
echo
echo "curl -s -X POST http://127.0.0.1:12551/create-account  -F "clientId=$CLIENT_ID" -F "creditLimit=0" -F "initialAmount=1000""
RESPONSE=$(curl -s -X POST http://127.0.0.1:12551/create-account?clientId -F "clientId=$CLIENT_ID" -F "creditLimit=0" -F "initialAmount=1000")
ACCOUNT2=$(echo $RESPONSE | jq -r '.accountId.value')


echo
echo "Created account $ACCOUNT2"

echo
curl -s -X GET  http://127.0.0.1:12551/find -F "clientId=$CLIENT_ID" | jq
echo


echo
echo Order payment
echo
echo "curl -s -X POST http://127.0.0.1:12551/order-payment -F "clientId=$CLIENT_ID" -F "from=$ACCOUNT2" -F "to=$ACCOUNT1" -F "amount=100""
curl -s -X POST http://127.0.0.1:12551/order-payment -F "clientId=$CLIENT_ID" -F "from=$ACCOUNT2" -F "to=$ACCOUNT1" -F "amount=100"

echo
echo Order payment
echo
echo "curl -s -X POST http://127.0.0.1:12551/order-payment -F "clientId=$CLIENT_ID" -F "from=$ACCOUNT2" -F "to=$ACCOUNT1" -F "amount=100""
curl -s -X POST http://127.0.0.1:12551/order-payment -F "clientId=$CLIENT_ID" -F "from=$ACCOUNT2" -F "to=$ACCOUNT1" -F "amount=100"

echo
curl -s -X GET  http://127.0.0.1:12551/find -F "clientId=$CLIENT_ID" | jq
echo
