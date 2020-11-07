URL1="http://127.0.0.1:12551"
URL2="http://127.0.0.1:12552"


function print_client {
  CLIENT_ID=$1
  curl -s -X GET $URL2/find -F "client-id=$CLIENT_ID" | jq
}

function create_client {
  CLIENT_NAME=$1
  CLIENT_ID=$(curl -s -X POST $URL1/create -F "client-name=$CLIENT_NAME" | jq -r '.client.id.value')
  echo $CLIENT_ID
}

function activate_client {
  CLIENT_ID=$1
  curl -s -X POST  $URL1/activate -F "client-id=$CLIENT_ID"
}

function deactivate_client {
  CLIENT_ID=$1
  curl -s -X POST  $URL1/deactivate -F "client-id=$CLIENT_ID"
}

function create_account {
  CLIENT_ID=$1
  CREDIT_LIMIT=$2
  INITIAL_AMOUNT=$3
  ACCOUINT_ID=$(curl -s -X POST $URL1/create-account?clientId -F "client-id=$CLIENT_ID" -F "credit-limit=$CREDIT_LIMIT" -F "initial-amount=$INITIAL_AMOUNT"  | jq -r '.accountId.value')
  echo $ACCOUINT_ID
}

function create_credit_account {
  CLIENT_ID=$1
  CREDIT_LIMIT=$2
  create_account $CLIENT_ID $CREDIT_LIMIT 0
}

function create_debit_account {
  CLIENT_ID=$1
  INITIAL_AMOUNT=$2
  create_account $CLIENT_ID 0 $INITIAL_AMOUNT
}

function order_payment {
  CLIENT_ID=$1
  SOURCE_ACCOUNT=$2
  TARGET_ACCOUNT=$3
  AMOUNT=$4
  curl -s -X POST $URL1/order-payment -F "client-id=$CLIENT_ID" -F "from=$SOURCE_ACCOUNT" -F "to=$TARGET_ACCOUNT" -F "amount=$AMOUNT"
}

