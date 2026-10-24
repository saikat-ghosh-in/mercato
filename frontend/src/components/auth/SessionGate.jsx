import { useDispatch, useSelector } from "react-redux";
import { initializeAuth } from "../../reduxStore/actions/authActions.js";

export default function SessionGate({ children }) {
  const dispatch = useDispatch();
  const { sessionStatus, sessionError } = useSelector(state => state.auth);
  if (["idle", "loading"].includes(sessionStatus)) return <div className="site-container route-loading" role="status" aria-label="Checking your session"><div /><div /></div>;
  if (sessionStatus === "error") return <div className="site-container state-panel" role="alert"><h1>We couldn’t check your session</h1><p>{sessionError}</p><button className="button button-dark" onClick={() => dispatch(initializeAuth())}>Try again</button></div>;
  return children;
}
