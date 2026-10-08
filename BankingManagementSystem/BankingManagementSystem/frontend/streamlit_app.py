# Streamlit frontend for the Banking Management System.
# It only shows screens; all banking rules run in the Java API.

import pandas as pd
import requests
import streamlit as st

API = "http://localhost:8080/api"

st.set_page_config(page_title="Banking Management System", page_icon="🏦", layout="wide")


# ---------------------------------------------------------------- API helpers
def call_api(method, path, payload=None, params=None, headers=None):
    """Calls the Java API and returns (ok, data). Never raises."""
    try:
        resp = requests.request(method, API + path, json=payload, params=params,
                                headers=headers, timeout=5)
        data = resp.json()
    except requests.exceptions.ConnectionError:
        return False, {"error": "Cannot reach the backend. Is it running on port 8080?"}
    except Exception as exc:
        return False, {"error": f"Unexpected error: {exc}"}
    return bool(data.get("success")), data


def backend_is_up():
    ok, _ = call_api("GET", "/health")
    return ok


def money(value):
    return f"₹ {float(value):,.2f}"


# -------------------------------------------------------------- session state
st.session_state.setdefault("account", None)      # logged-in customer (dict)
st.session_state.setdefault("admin_token", None)  # admin session token


def logout():
    st.session_state.account = None
    st.session_state.admin_token = None


def refresh_account(data):
    """Stores the updated account returned by the API."""
    st.session_state.account = data["account"]


# ------------------------------------------------------------------- screens
def register_page():
    st.subheader("Open a new account")
    with st.form("register_form"):
        name = st.text_input("Full name")
        email = st.text_input("Email")
        password = st.text_input("Password (min 6 characters)", type="password")
        account_type = st.selectbox("Account type", ["SAVINGS", "CURRENT"])
        deposit = st.number_input("Initial deposit", min_value=0.0, step=100.0, format="%.2f")
        submitted = st.form_submit_button("Register")
    if submitted:
        ok, data = call_api("POST", "/register", {
            "name": name, "email": email, "password": password,
            "accountType": account_type, "initialDeposit": f"{deposit:.2f}"})
        if ok:
            st.success(f"Account created! Your account number is "
                       f"{data['account']['accountNumber']}. Use it to log in.")
        else:
            st.error(data.get("error", "Registration failed"))


def login_page():
    st.subheader("Customer login")
    with st.form("login_form"):
        account_number = st.text_input("Account number")
        password = st.text_input("Password", type="password")
        submitted = st.form_submit_button("Login")
    if submitted:
        ok, data = call_api("POST", "/login",
                            {"accountNumber": account_number, "password": password})
        if ok:
            refresh_account(data)
            st.rerun()
        else:
            st.error(data.get("error", "Login failed"))


def history_table(transactions):
    if not transactions:
        st.info("No transactions yet.")
        return
    df = pd.DataFrame(transactions).rename(columns={
        "id": "ID", "accountNumber": "Account", "type": "Type", "amount": "Amount",
        "balanceAfter": "Balance after", "timestamp": "Time", "details": "Details"})
    st.dataframe(df, use_container_width=True, hide_index=True)


def money_form(title, button, path, number, extra_fields=False):
    """Shared form for deposit / withdraw / transfer."""
    with st.form(f"{path}_form"):
        to_account = st.text_input("Recipient account number") if extra_fields else None
        amount = st.number_input(title, min_value=0.0, step=100.0, format="%.2f")
        if st.form_submit_button(button):
            if extra_fields:
                payload = {"fromAccount": number, "toAccount": to_account, "amount": f"{amount:.2f}"}
            else:
                payload = {"accountNumber": number, "amount": f"{amount:.2f}"}
            ok, data = call_api("POST", path, payload)
            if ok:
                refresh_account(data)
                st.session_state.flash = f"{button} successful"
                st.rerun()
            else:
                st.error(data.get("error"))


def customer_dashboard():
    acc = st.session_state.account
    number = acc["accountNumber"]
    st.subheader(f"Welcome, {acc['name']}")
    if st.session_state.get("flash"):
        st.success(st.session_state.pop("flash"))

    col1, col2, col3 = st.columns(3)
    col1.metric("Balance", money(acc["balance"]))
    col2.metric("Account number", number)
    col3.metric("Account type", acc["accountType"])
    st.caption(f"Email: {acc['email']}")

    deposit_tab, withdraw_tab, transfer_tab, history_tab = st.tabs(
        ["Deposit", "Withdraw", "Transfer", "History"])
    with deposit_tab:
        money_form("Amount to deposit", "Deposit", "/deposit", number)
    with withdraw_tab:
        money_form("Amount to withdraw", "Withdraw", "/withdraw", number)
    with transfer_tab:
        money_form("Amount to transfer", "Transfer", "/transfer", number, extra_fields=True)
    with history_tab:
        ok, data = call_api("GET", "/transactions", params={"accountNumber": number})
        if ok:
            history_table(data["transactions"])
        else:
            st.error(data.get("error"))


def admin_login_page():
    st.subheader("Admin login")
    with st.form("admin_form"):
        username = st.text_input("Username")
        password = st.text_input("Password", type="password")
        submitted = st.form_submit_button("Login as admin")
    if submitted:
        ok, data = call_api("POST", "/admin/login",
                            {"username": username, "password": password})
        if ok:
            st.session_state.admin_token = data["token"]
            st.rerun()
        else:
            st.error(data.get("error", "Admin login failed"))


def admin_dashboard():
    st.subheader("Admin dashboard")
    headers = {"X-Admin-Token": st.session_state.admin_token}

    ok, accounts = call_api("GET", "/admin/accounts", headers=headers)
    ok2, txns = call_api("GET", "/admin/transactions", headers=headers)
    if not (ok and ok2):
        st.error((accounts if not ok else txns).get("error"))
        if st.button("Back to admin login"):
            st.session_state.admin_token = None
            st.rerun()
        return

    total = sum(float(a["balance"]) for a in accounts["accounts"])
    c1, c2, c3 = st.columns(3)
    c1.metric("Accounts", len(accounts["accounts"]))
    c2.metric("Transactions", len(txns["transactions"]))
    c3.metric("Total balance", money(total))

    st.markdown("#### All accounts")
    if accounts["accounts"]:
        st.dataframe(pd.DataFrame(accounts["accounts"]), use_container_width=True, hide_index=True)
    else:
        st.info("No accounts yet.")
    st.markdown("#### All transactions")
    history_table(txns["transactions"])


# ---------------------------------------------------------------------- main
st.title("🏦 Banking Management System")

if not backend_is_up():
    st.warning("⚠️ Backend is offline. Start it with backend/run_backend.sh "
               "(or run_backend.bat on Windows), then refresh this page.")

st.sidebar.title("Menu")
choice = None
if st.session_state.account or st.session_state.admin_token:
    who = st.session_state.account["name"] if st.session_state.account else "admin"
    st.sidebar.write(f"Logged in as **{who}**")
    if st.sidebar.button("Logout"):
        logout()
        st.rerun()
else:
    choice = st.sidebar.radio("Go to", ["Login", "Register", "Admin"])

if st.session_state.account:
    customer_dashboard()
elif st.session_state.admin_token:
    admin_dashboard()
elif choice == "Login":
    login_page()
elif choice == "Register":
    register_page()
else:
    admin_login_page()
