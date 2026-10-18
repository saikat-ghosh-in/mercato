import { Component } from "react";

export default class ErrorBoundary extends Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error, info) { console.error("Application error", error, info); }
  render() {
    if (this.state.failed) return <div className="site-container home-section"><div className="state-panel" role="alert"><h1>Something didn’t load correctly.</h1><p>Refresh the page to try again. Your saved bag will still be here.</p><button className="button button-dark" onClick={() => window.location.reload()}>Refresh page</button></div></div>;
    return this.props.children;
  }
}
