#!/usr/bin/env bash

# Shared, sourceable validation for the fast-input test contract.
# Keep this script compatible with the Bash shipped by macOS runners.

fast_input_contract_error() {
  echo "FAST_INPUT_CONTRACT_ERROR: $*" >&2
  return 2
}

fast_input_contract_compact() {
  printf '%s' "$1" | tr -d '[:space:]'
}

fast_input_contract_has_token() {
  local token="$1"
  local csv="$2"

  case ",${csv}," in
    *,"${token}",*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

fast_input_contract_normalize_csv() {
  local raw="$1"
  local kind="$2"
  local compact
  local token
  local normalized=""
  local -a tokens

  compact="$(fast_input_contract_compact "$raw")"
  if [ -z "$compact" ]; then
    fast_input_contract_error "${kind} must not be empty"
    return 2
  fi

  IFS=',' read -r -a tokens <<< "$compact"
  for token in "${tokens[@]}"; do
    if [ -z "$token" ]; then
      fast_input_contract_error "${kind} contains an empty value"
      return 2
    fi

    case "$kind" in
      surfaces)
        token="$(printf '%s' "$token" | tr '[:lower:]' '[:upper:]')"
        case "$token" in
          TENKEY|GOJUON|SUMIRE|QWERTY|ROMAJI|CUSTOM) ;;
          *)
            fast_input_contract_error "unknown surface: ${token}"
            return 2
            ;;
        esac
        ;;
      columns)
        case "$token" in
          1|2|3) ;;
          *)
            fast_input_contract_error "unknown column: ${token}"
            return 2
            ;;
        esac
        ;;
      sumireMethods)
        token="$(printf '%s' "$token" | tr '[:upper:]' '[:lower:]')"
        case "$token" in
          toggle|flick|switch-mode-effective) ;;
          *)
            fast_input_contract_error "unknown Sumire method: ${token}"
            return 2
            ;;
        esac
        ;;
      *)
        fast_input_contract_error "unknown contract field: ${kind}"
        return 2
        ;;
    esac

    if fast_input_contract_has_token "$token" "$normalized"; then
      fast_input_contract_error "${kind} contains a duplicate value: ${token}"
      return 2
    fi

    if [ -n "$normalized" ]; then
      normalized="${normalized},"
    fi
    normalized="${normalized}${token}"
  done

  printf '%s' "$normalized"
}

fast_input_contract_normalize() {
  local rounds="$1"
  local surfaces="$2"
  local columns="$3"
  local sumire_methods="$4"
  local compact_surfaces
  local compact_methods
  local normalized_surface_case
  local normalized_method_case
  local normalized_surfaces
  local normalized_columns
  local normalized_methods

  case "$rounds" in
    ''|*[!0-9]*)
      fast_input_contract_error "rounds must be a positive integer: ${rounds}"
      return 2
      ;;
  esac
  if [ "$rounds" -le 0 ]; then
    fast_input_contract_error "rounds must be greater than zero"
    return 2
  fi
  if [ "$rounds" -gt 10 ]; then
    fast_input_contract_error "rounds must be <= 10"
    return 2
  fi

  compact_surfaces="$(fast_input_contract_compact "$surfaces")"
  normalized_surface_case="$(printf '%s' "$compact_surfaces" | tr '[:lower:]' '[:upper:]')"
  if [[ "$normalized_surface_case" == *,* && "$normalized_surface_case" == *ALL* ]]; then
    fast_input_contract_error "surfaces cannot combine ALL with a surface name"
    return 2
  fi
  case "$normalized_surface_case" in
    ALL)
      normalized_surfaces="ALL"
      ;;
    *)
      normalized_surfaces="$(fast_input_contract_normalize_csv "$compact_surfaces" surfaces)" || return $?
      ;;
  esac

  normalized_columns="$(fast_input_contract_normalize_csv "$columns" columns)" || return $?

  compact_methods="$(fast_input_contract_compact "$sumire_methods")"
  normalized_method_case="$(printf '%s' "$compact_methods" | tr '[:upper:]' '[:lower:]')"
  if [[ "$normalized_method_case" == *,* && "$normalized_method_case" == *all* ]]; then
    fast_input_contract_error "sumireMethods cannot combine ALL with a method name"
    return 2
  fi
  case "$normalized_method_case" in
    all)
      normalized_methods="ALL"
      ;;
    *)
      normalized_methods="$(fast_input_contract_normalize_csv "$compact_methods" sumireMethods)" || return $?
      ;;
  esac

  FAST_INPUT_CONTRACT_ROUNDS="$rounds"
  FAST_INPUT_CONTRACT_SURFACES="$normalized_surfaces"
  FAST_INPUT_CONTRACT_COLUMNS="$normalized_columns"
  FAST_INPUT_CONTRACT_SUMIRE_METHODS="$normalized_methods"
  export FAST_INPUT_CONTRACT_ROUNDS
  export FAST_INPUT_CONTRACT_SURFACES
  export FAST_INPUT_CONTRACT_COLUMNS
  export FAST_INPUT_CONTRACT_SUMIRE_METHODS
}
