#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration ---
KOTLIN_PROJECT_DIR="/Users/josephmalone/IdeaProjects/tic_tac_toe_compose"
HARDHAT_PROJECT_DIR="/Users/josephmalone/tic-tac-toe-smart-contract"
# Output directory for generated Java code
OUTPUT_DIR="$KOTLIN_PROJECT_DIR/src/main/java"
# Package name for generated Java code
PACKAGE_NAME="vision.salient.contracts"
# Paths to necessary JARs
CODEGEN_JAR="/Users/josephmalone/Downloads/codegen-4.8.7.jar"
PICOCLI_JAR="/Users/josephmalone/Downloads/picocli-4.7.5.jar"
# Directory where Hardhat compilation places the JSON artifacts
HARDHAT_ARTIFACTS_DIR="$HARDHAT_PROJECT_DIR/contracts/artifacts" # Corrected path

# --- Ensure JARs exist ---
# ...(Checks remain the same)...
if [ ! -f "$CODEGEN_JAR" ]; then echo "ERROR: codegen JAR missing"; exit 1; fi
if [ ! -f "$PICOCLI_JAR" ]; then echo "ERROR: picocli JAR missing"; exit 1; fi

# --- Function to clean JSON, extract ABI/BIN, and generate wrapper ---
generate_wrapper() {
  local contract_name=$1
  local input_json="$HARDHAT_ARTIFACTS_DIR/${contract_name}.json"
  # Create temporary files for extracted ABI and BIN
  local temp_abi_file=$(mktemp)
  local temp_bin_file=$(mktemp)
  # Create a temp file for the main object if needed (though extraction below is better)
  # local clean_json_temp=$(mktemp) # Not strictly needed if we extract directly

  echo "Processing $contract_name..."

  if [ ! -f "$input_json" ]; then
      echo "ERROR: Input JSON artifact not found at $input_json"
      # Optional: Attempt recompile (removed for brevity, add back if needed)
      # (cd "$HARDHAT_PROJECT_DIR" && npx hardhat compile) || { echo "Hardhat compile failed"; exit 1; }
      if [ ! -f "$input_json" ]; then echo "ERROR: Input JSON still not found: $input_json"; exit 1; fi
  fi

  echo "Extracting ABI and BIN from $input_json..."
  # Extract ABI array (look for top-level 'abi' or nested 'data.abi')
  jq --raw-output '(.abi // .data.abi // empty)' "$input_json" > "$temp_abi_file"
  # Extract Bytecode object string (look for top-level 'bytecode' or nested 'data.bytecode.object')
  # IMPORTANT: web3j expects the BIN file to contain the bytecode *without* the "0x" prefix
  jq --raw-output '(.bytecode // .data.bytecode.object // empty)' "$input_json" | sed 's/^0x//' > "$temp_bin_file"


  # Check if extraction produced non-empty files
   if [ ! -s "$temp_abi_file" ] || [ ! -s "$temp_bin_file" ]; then
       echo "ERROR: Failed to extract ABI or BIN from $input_json."
       echo "ABI temp: $temp_abi_file"
       echo "BIN temp: $temp_bin_file"
       rm "$temp_abi_file" "$temp_bin_file" # Clean up
       exit 1
   fi

  echo "Generating Java wrapper for $contract_name using extracted files..."
  # Run the codegen using the -a and -b flags with the temporary files
  java -cp "$CODEGEN_JAR:$PICOCLI_JAR" org.web3j.codegen.SolidityFunctionWrapperGenerator \
    -a "$temp_abi_file" \
    -b "$temp_bin_file" \
    -o "$OUTPUT_DIR" \
    -p "$PACKAGE_NAME" \
    || { echo "ERROR: Web3j codegen failed for $contract_name"; rm "$temp_abi_file" "$temp_bin_file"; exit 1; }

  # Clean up temporary files
  rm "$temp_abi_file" "$temp_bin_file"
  echo "Successfully generated wrapper for $contract_name."
}

# --- Main Execution ---
echo "Starting wrapper generation..."
mkdir -p "$OUTPUT_DIR/$PACKAGE_NAME" # Create full package path

generate_wrapper "TicTacToeFactory"
generate_wrapper "MultiPlayerTicTacToe"

# ...(Success messages remain the same)...
echo "-------------------------------------"
echo "Wrapper generation process complete."
echo "Check for Java files in: $OUTPUT_DIR/$PACKAGE_NAME"
# ...
echo "-------------------------------------"

exit 0