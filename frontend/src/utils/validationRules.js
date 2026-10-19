export const usernameRules = {
    required: "Username is required",
    minLength: {
        value: 3,
        message: "Username must be at least 3 characters",
    },
    maxLength: {
        value: 20,
        message: "Username must be at most 20 characters",
    },
    pattern: {
        value: /^[a-zA-Z0-9_]+$/,
        message: "Only letters, numbers, and underscores allowed",
    },
};

export const emailRules = {
    required: "Email is required",
    pattern: {
        value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
        message: "Enter a valid email address",
    },
};

export const passwordRules = {
    required: "Password is required",
    minLength: {
        value: 8,
        message: "Password must be at least 8 characters",
    },
    validate: {
        hasUpper: (v) =>
            /[A-Z]/.test(v) || "Must contain at least one uppercase letter",
        hasLower: (v) =>
            /[a-z]/.test(v) || "Must contain at least one lowercase letter",
        hasNumber: (v) =>
            /\d/.test(v) || "Must contain at least one number",
    },
};

export const phoneRules = {
    required: "Phone number is required",
    pattern: {
        value: /^[0-9]{10}$/,
        message: "Invalid phone number",
    },
};
