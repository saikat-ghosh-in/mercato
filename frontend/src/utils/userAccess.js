export const accessLabels = {
  DEACTIVATE: "Deactivate account", REACTIVATE: "Reactivate account",
  SUSPEND_SELLING: "Suspend selling", RESTORE_SELLING: "Restore selling",
  SELLER_APPROVED: "Seller application approved", SELLER_REJECTED: "Seller application rejected",
};
export function accountStatus(user) {
  if (!user.enabled) return user.selfDeactivated ? "Paused" : "Deactivated";
  if (user.accountLocked) return "Locked";
  return user.emailVerified ? "Active" : "Awaiting verification";
}
export function accessActions(user) {
  if (user.roles?.includes("ROLE_ADMIN")) return [];
  const actions = [user.enabled ? "DEACTIVATE" : "REACTIVATE"];
  if (!user.enabled && user.selfDeactivated) actions.push("DEACTIVATE");
  if (user.roles?.includes("ROLE_SELLER") && !user.sellerSuspended) actions.push("SUSPEND_SELLING");
  else if (user.sellerStatus === "APPROVED" && user.enabled && user.emailVerified && !user.accountLocked) actions.push("RESTORE_SELLING");
  return actions.filter(action => action !== "REACTIVATE" || !user.accountLocked);
}
export function filterUsers(users, { search = "", role = "", status = "", sort = "newest" }) {
  const term = search.trim().toLocaleLowerCase();
  return users.filter(user => (!term || [user.username, user.email, user.firstName, user.lastName, user.sellerDisplayName].some(value => value?.toLocaleLowerCase().includes(term)))
    && (!role || user.roles?.includes(role))
    && (!status || (status === "selling-suspended" ? user.sellerSuspended : accountStatus(user) === status)))
    .sort((a, b) => (sort === "name" ? (a.firstName || a.username).localeCompare(b.firstName || b.username) : (sort === "oldest" ? 1 : -1) * (Date.parse(a.createdAt || 0) - Date.parse(b.createdAt || 0))) || a.userId.localeCompare(b.userId));
}
