export function isSellerAccount(user) {
  return Boolean(user?.roles?.includes("ROLE_SELLER") || ["PENDING", "REJECTED"].includes(user?.sellerStatus));
}
export function accountLanding(user) {
  return isSellerAccount(user) && !user?.roles?.includes("ROLE_ADMIN") ? "/seller/dashboard" : "/";
}
export function loginDestination(user, redirect = "/") {
  const safe = redirect.startsWith("/") && !redirect.startsWith("//") ? redirect : "/";
  if (safe.startsWith("/admin/") && !user?.roles?.includes("ROLE_ADMIN")) return accountLanding(user);
  if (safe.startsWith("/seller/dashboard") && !isSellerAccount(user) && !user?.roles?.includes("ROLE_ADMIN")) return "/profile";
  return safe === "/" ? accountLanding(user) : safe;
}
