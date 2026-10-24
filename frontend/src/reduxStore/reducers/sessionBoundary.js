export const withSessionBoundary = reducer => (state, action) => {
  if (action.type === "SESSION_STATE" && state?.auth.sessionEpoch !== action.payload.epoch) state = undefined;
  return reducer(state, action);
};
