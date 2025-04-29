#!/bin/bash
set -e

# --- Configuration ---
KOTLIN_PROJECT_DIR="/Users/josephmalone/IdeaProjects/tic_tac_toe_compose"
HARDHAT_PROJECT_DIR="/Users/josephmalone/tic-tac-toe-smart-contract"
OUTPUT_DIR="$KOTLIN_PROJECT_DIR/src/main/java"
PACKAGE_NAME="vision.salient.contracts"
CODEGEN_JAR="/Users/josephmalone/Downloads/codegen-4.8.7.jar" # <--- VERIFY PATH
PICOCLI_JAR="/Users/josephmalone/Downloads/picocli-4.7.5.jar" # <--- VERIFY PATH
# --- Use STANDARD Hardhat artifact directory ---
HARDHAT_ARTIFACTS_DIR="$HARDHAT_PROJECT_DIR/artifacts"

# --- Ensure Dependencies Exist ---
if ! command -v jq &> /dev/null; then echo "ERROR: jq is not installed..."; exit 1; fi
if [ ! -f "$CODEGEN_JAR" ]; then echo "ERROR: codegen JAR missing..."; exit 1; fi
if [ ! -f "$PICOCLI_JAR" ]; then echo "ERROR: picocli JAR missing..."; exit 1; fi

# --- Function to generate wrapper ---
generate_wrapper() {
  local contract_name=$1
  # --- Use STANDARD Hardhat artifact path ---
  local input_json="$HARDHAT_ARTIFACTS_DIR/contracts/${contract_name}.sol/${contract_name}.json"
  local temp_abi_file=$(mktemp "/tmp/${contract_name}_abi_XXXXXX")
  local temp_bin_file=$(mktemp "/tmp/${contract_name}_bin_XXXXXX")

  echo "Processing $contract_name..."
  if [ ! -f "$input_json" ]; then
      echo "ERROR: Standard Hardhat artifact missing: $input_json"
      echo "Ensure 'npx hardhat compile' ran successfully in $HARDHAT_PROJECT_DIR"
      exit 1
  fi

  echo "Extracting ABI and BIN from standard artifact: $input_json..."
  # Extract top-level '.abi' array
  jq --compact-output '.abi // empty' "$input_json" > "$temp_abi_file"
  # Extract top-level '.bytecode' string and remove 0x prefix
  jq --raw-output '.bytecode // empty' "$input_json" | sed 's/^0x//' > "$temp_bin_file"

  # Check extraction
   if [ ! -s "$temp_abi_file" ] || [ ! -s "$temp_bin_file" ]; then
       echo "ERROR: Failed to extract ABI or BIN from $input_json."
       rm "$temp_abi_file" "$temp_bin_file"; exit 1
   fi
   bytecode_content=$(cat "$temp_bin_file")
   if [ -z "$bytecode_content" ] || [ "$bytecode_content" == "null" ] || [ "$bytecode_content" == "00" ]; then
        echo "ERROR: Extracted bytecode is empty, null, or invalid from $input_json."
        rm "$temp_abi_file" "$temp_bin_file"; exit 1
   fi

  echo "Generating Java wrapper for $contract_name..."
  # Run codegen with -a and -b flags using TEMP files
  java -cp "$CODEGEN_JAR:$PICOCLI_JAR" org.web3j.codegen.SolidityFunctionWrapperGenerator \
    -a "$temp_abi_file" \
    -b "$temp_bin_file" \
    -o "$OUTPUT_DIR" \
    -p "$PACKAGE_NAME" \
    || { echo "ERROR: Web3j codegen failed for $contract_name"; rm "$temp_abi_file" "$temp_bin_file"; exit 1; }

  # Cleanup
  rm "$temp_abi_file" "$temp_bin_file"
  echo "Successfully generated wrapper for $contract_name."
}

# --- Main Execution ---
echo "Starting wrapper generation..."
mkdir -p "$OUTPUT_DIR/$(echo $PACKAGE_NAME | tr . /)"

# No need to run compile here if we assume it was done prior

generate_wrapper "TicTacToeFactory"
generate_wrapper "MultiPlayerTicTacToe"

echo "-------------------------------------"
echo "✅ Wrapper generation process complete."
echo "Check for Java files in: $OUTPUT_DIR/$PACKAGE_NAME"
echo "Make sure build.gradle.kts includes 'src/main/java' in sourceSets.main.java.srcDirs"
echo "Remember to Sync Gradle in your IDE."
echo "-------------------------------------"

exit 0